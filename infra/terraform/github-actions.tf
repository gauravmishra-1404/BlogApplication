# Lets GitHub Actions deploy new commits straight to Beanstalk - the AWS-side replacement for
# what Render gave for free (push to master -> auto-deploy). See .github/workflows/deploy.yml for
# the workflow itself; this file only sets up what it needs to authenticate to AWS.
#
# OIDC federation, not a long-lived IAM user access key pasted into GitHub Secrets - GitHub's
# runner requests a short-lived token from GitHub's own OIDC provider, exchanges it for temporary
# AWS credentials via sts:AssumeRoleWithWebIdentity, and nothing durable ever sits in GitHub at
# all. The trust condition below is scoped to exactly this repo AND exactly the master branch, so
# a workflow running from a fork or a feature-branch PR can never assume this role.

data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

# One OIDC provider per AWS account, not per-project - if this project ever adds a second repo
# that also needs GitHub Actions -> AWS, it would reuse this same provider with its own separate
# role/trust-condition, not create a second one (AWS only allows one provider per unique URL).
resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Locked to pushes on master specifically (not "any ref in this repo") - a PR from a
    # feature branch, or from a fork, gets a token whose `sub` claim doesn't match this and
    # can't assume the role, so it can't deploy.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:gauravmishra-1404/BlogApplication:ref:refs/heads/master"]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "${var.project}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
}

# Exactly what a deploy needs and nothing else - upload the new bundle, register it as an
# application version, point the environment at it, and poll status while it rolls out. No
# access to RDS, no IAM/security-group permissions, nothing that could touch data or other infra
# even if a workflow run were somehow compromised.
resource "aws_iam_role_policy" "github_actions_deploy" {
  name = "${var.project}-github-deploy"
  role = aws_iam_role.github_actions_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        # GetObject alongside PutObject is not redundant here: `aws s3 cp` (the upload) only
        # needs PutObject, but elasticbeanstalk:CreateApplicationVersion has AWS's own Beanstalk
        # service fetch the object back out of S3 using THIS role's credentials to register it -
        # without GetObject too, that step fails with "Unable to download from S3 location ...
        # Forbidden", which is exactly what happened on this workflow's first real run.
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = "${aws_s3_bucket.lambda_artifacts.arn}/beanstalk/*"
      },
      {
        Effect = "Allow"
        # AWS's own account/region-level Beanstalk storage bucket (elasticbeanstalk-<region>-
        # <account>), auto-provisioned the first time anything uses Elastic Beanstalk in this
        # account - already exists here, but elasticbeanstalk:UpdateEnvironment unconditionally
        # runs a whole sequence of idempotent "ensure this bucket is configured right" S3 calls
        # regardless (CreateBucket, then bucket-ownership/public-access-block settings, ...), and
        # IAM evaluates each one before AWS discovers there's nothing to actually change. Two real
        # UpdateEnvironment failures (s3:CreateBucket, then s3:PutBucketOwnershipControls) came
        # from exactly this, one action at a time - rather than keep whack-a-moling each one as it
        # surfaces, this action list is copied verbatim from AWS's own current
        # AdministratorAccess-AWSElasticBeanstalk managed policy's elasticbeanstalk-* bucket
        # statement (pulled live via `aws iam get-policy-version`), just re-scoped to this one
        # specific bucket ARN instead of AWS's own wildcard elasticbeanstalk-*.
        Action = [
          "s3:CreateBucket",
          "s3:GetBucket*",
          "s3:ListBucket",
          "s3:PutBucketPolicy",
          "s3:PutBucketPublicAccessBlock",
          "s3:PutBucketOwnershipControls"
        ]
        Resource = "arn:aws:s3:::elasticbeanstalk-${var.aws_region}-${data.aws_caller_identity.current.account_id}"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:Delete*", "s3:Get*", "s3:Put*"]
        Resource = "arn:aws:s3:::elasticbeanstalk-${var.aws_region}-${data.aws_caller_identity.current.account_id}/*"
      },
      {
        Effect = "Allow"
        # elasticbeanstalk:UpdateEnvironment on an EXISTING environment doesn't touch EC2/ASG/ELB
        # directly - it drives them all through the CloudFormation stack Beanstalk itself creates
        # per environment (awseb-e-<env-id>-stack), reading/updating that stack's template as part
        # of rolling out a new app version. A real UpdateEnvironment call failed on GetTemplate
        # here. CreateStack/DeleteStack deliberately excluded even though AWS's own bundled policy
        # (AdministratorAccess-AWSElasticBeanstalk, pulled live) includes them - this role only
        # ever deploys a new version to an environment that already exists, never creates or tears
        # one down, so granting those two would be privilege this role has no legitimate use for.
        Action = [
          "cloudformation:CancelUpdateStack",
          "cloudformation:ContinueUpdateRollback",
          "cloudformation:GetTemplate",
          "cloudformation:ListStackResources",
          "cloudformation:SignalResource",
          "cloudformation:TagResource",
          "cloudformation:UntagResource",
          "cloudformation:UpdateStack"
        ]
        Resource = "arn:aws:cloudformation:${var.aws_region}:${data.aws_caller_identity.current.account_id}:stack/awseb-*"
      },
      {
        Effect = "Allow"
        # Beanstalk suspends the environment's own Auto Scaling group's processes for the
        # duration of a rolling deploy (stops the ASG from fighting the deployment by replacing
        # instances mid-rollout on its own), then resumes them once done. A real deploy failed
        # here with "not authorized to perform: autoscaling:SuspendProcesses" - the workflow
        # itself still reported success (aws elasticbeanstalk wait environment-updated only waits
        # for the environment to leave "Updating" status, not for the deploy to have actually
        # succeeded), and the environment silently stayed on the old app version. AWS's own
        # bundled policy grants "autoscaling:*" on this resource pattern; scoped down here to
        # just the two actions this role's actual job (routine deploys) needs.
        Action = [
          "autoscaling:SuspendProcesses",
          "autoscaling:ResumeProcesses"
        ]
        Resource = "arn:aws:autoscaling:${var.aws_region}:${data.aws_caller_identity.current.account_id}:autoScalingGroup:*:autoScalingGroupName/awseb-e-*"
      },
      {
        Effect = "Allow"
        # Read-only Describe/Get/List/Estimate/Validate calls across the services Beanstalk
        # orchestrates under the hood (autoscaling, cloudformation, cloudwatch, ec2, elb, logs,
        # rds, ...) - copied verbatim from AWS's own AdministratorAccess-AWSElasticBeanstalk
        # policy's first statement. Granted account-wide (Resource: *) same as AWS's own policy
        # does, because most of these Describe/List-style actions don't support resource-level
        # ARN scoping in the first place - but every action here is purely informational (no
        # Create/Put/Delete/Update), so the blast radius is "can read metadata", not "can change
        # anything". Added proactively rather than one-Describe-call-at-a-time, after 3 rounds of
        # exactly that for CreateBucket/PutBucketOwnershipControls/GetTemplate already.
        Action = [
          "autoscaling:Describe*",
          "cloudformation:Describe*",
          "cloudformation:Get*",
          "cloudformation:List*",
          "cloudwatch:DescribeAlarms",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
          "ec2:Describe*",
          "elasticloadbalancing:Describe*",
          "logs:Describe*",
          "rds:Describe*"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "elasticbeanstalk:CreateApplicationVersion",
          "elasticbeanstalk:UpdateEnvironment",
          "elasticbeanstalk:DescribeEnvironments",
          "elasticbeanstalk:DescribeApplicationVersions",
          "elasticbeanstalk:DescribeEvents"
        ]
        # Scoped to exactly this app/environment/version-namespace, not every Beanstalk resource
        # in the account - a bare "*" isn't needed here, EB's own actions support resource-level
        # ARNs same as S3's do.
        Resource = [
          "arn:aws:elasticbeanstalk:${var.aws_region}:${data.aws_caller_identity.current.account_id}:application/${aws_elastic_beanstalk_application.main.name}",
          "arn:aws:elasticbeanstalk:${var.aws_region}:${data.aws_caller_identity.current.account_id}:applicationversion/${aws_elastic_beanstalk_application.main.name}/*",
          "arn:aws:elasticbeanstalk:${var.aws_region}:${data.aws_caller_identity.current.account_id}:environment/${aws_elastic_beanstalk_application.main.name}/${aws_elastic_beanstalk_environment.lb[0].name}",
          "arn:aws:elasticbeanstalk:${var.aws_region}::solutionstack/*"
        ]
        # CreateApplicationVersion also needs to reference the solution stack implicitly through
        # the platform the environment already uses - the solutionstack wildcard above is AWS's
        # own documented requirement for that action, not a scoping gap on this policy's part.
      }
    ]
  })
}

output "github_actions_role_arn" {
  description = "Paste this into the workflow's role-to-assume if it ever needs to change - see .github/workflows/deploy.yml."
  value       = aws_iam_role.github_actions_deploy.arn
}
