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
          "arn:aws:elasticbeanstalk:${var.aws_region}:${data.aws_caller_identity.current.account_id}:environment/${aws_elastic_beanstalk_application.main.name}/${aws_elastic_beanstalk_environment.main.name}",
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
