# Runs the main Spring Boot app itself - replacing Render's web service. Elastic Beanstalk on a
# single db.t4g... no, EC2 t3.micro instance (not Fargate) specifically to stay inside AWS's
# real 12-month EC2 free tier (750 instance-hours/month) - Fargate has no free tier at all, it
# bills per vCPU/memory-second from the very first task. Beanstalk gives most of what Fargate
# would have (managed deploys, health checks, auto-restart on crash) on top of that same
# free-tier EC2 instance, which is the actual reason it won out over both raw EC2 (all of that
# would be hand-rolled) and Fargate (not free) - see the cost conversation this came out of.
#
# Single-instance environment (no load balancer) - the ALB technically has its own 12-month free
# tier too, but skipping it entirely removes even the small risk of exceeding its free LCU
# allowance, keeps this even cheaper after year 1, and this app doesn't need the horizontal
# scaling an ALB exists to support at its current traffic. The instance gets a public IP and
# serves HTTP directly.

locals {
  beanstalk_cname_prefix = "bodhsea-app"
  beanstalk_app_base_url = "http://${local.beanstalk_cname_prefix}.${var.aws_region}.elasticbeanstalk.com"

  # Same "Terraform uploads an already-built artifact, doesn't build it itself" convention
  # lambda.tf already uses for the worker jars - run infra/terraform/package-beanstalk.sh before
  # `terraform apply`. It runs the Maven build itself (on whatever machine runs the script) and
  # stages only a pre-built jar + a lean runtime-only Dockerfile.deploy - deliberately NOT the
  # repo's own root Dockerfile's multi-stage approach (which compiles ON the target instance): a
  # real deploy did that and overwhelmed the target t3.micro (1GB RAM) badly enough to make it
  # stop responding entirely mid-build. See package-beanstalk.sh's own comment for the full story.
  beanstalk_bundle = "${path.module}/build/beanstalk-app.zip"
}

resource "aws_s3_object" "beanstalk_bundle" {
  bucket = aws_s3_bucket.lambda_artifacts.id # shared deploy-artifacts bucket, not lambda-specific despite the name
  key    = "beanstalk/bodhsea-app-${filemd5(local.beanstalk_bundle)}.zip"
  source = local.beanstalk_bundle
  etag   = filemd5(local.beanstalk_bundle)
}

resource "aws_elastic_beanstalk_application" "main" {
  name        = "${var.project}-app"
  description = "Bodh Sea - the main Spring Boot web app"
}

resource "aws_elastic_beanstalk_application_version" "main" {
  name        = "bundle-${substr(filemd5(local.beanstalk_bundle), 0, 12)}"
  application = aws_elastic_beanstalk_application.main.name
  bucket      = aws_s3_object.beanstalk_bundle.bucket
  key         = aws_s3_object.beanstalk_bundle.key
}

# Inbound HTTP from anywhere - this is the public-facing web app (equivalent to what Render's
# edge already does today). No inbound SSH rule on purpose; use EC2 Instance Connect / SSM
# Session Manager if shell access is ever needed, not a standing open port 22.
resource "aws_security_group" "app_instance" {
  name        = "${var.project}-app-instance"
  description = "The Beanstalk EC2 instance running the Spring Boot app"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- IAM: the service role Beanstalk itself assumes to manage resources on your behalf ---
data "aws_iam_policy_document" "beanstalk_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["elasticbeanstalk.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "beanstalk_service" {
  name               = "${var.project}-beanstalk-service"
  assume_role_policy = data.aws_iam_policy_document.beanstalk_assume.json
}

resource "aws_iam_role_policy_attachment" "beanstalk_service_health" {
  role       = aws_iam_role.beanstalk_service.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkEnhancedHealth"
}

# AWSElasticBeanstalkEnhancedHealth alone doesn't cover Auto Scaling describe permissions the
# service role needs to actually assess instance health - without this, the environment gets
# stuck at Yellow/Warning ("Access denied while accessing Auto Scaling using role...") even
# though the app itself is running fine, confirmed via a direct curl to the environment URL.
resource "aws_iam_role_policy_attachment" "beanstalk_service_core" {
  role       = aws_iam_role.beanstalk_service.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkService"
}

resource "aws_iam_role_policy_attachment" "beanstalk_service_updates" {
  role       = aws_iam_role.beanstalk_service.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkManagedUpdatesCustomerRolePolicy"
}

# --- IAM: the role/instance profile the EC2 instance itself runs as ---
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "beanstalk_ec2" {
  name               = "${var.project}-beanstalk-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "beanstalk_ec2_webtier" {
  role       = aws_iam_role.beanstalk_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier"
}

# Amazon Linux 2023 ships the SSM agent already installed - this policy is the only missing piece
# for `aws ssm start-session` shell access. This is the "SSM Session Manager if shell access is
# ever needed" alternative referenced in the app_instance security group's own comment above (no
# inbound SSH port needed either way - Session Manager tunnels over the agent's own outbound
# connection to the SSM service, nothing to open in the security group).
resource "aws_iam_role_policy_attachment" "beanstalk_ec2_ssm" {
  role       = aws_iam_role.beanstalk_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Same two scoped permissions the bodhsea-notification-service IAM user already has (iam.tf's
# app_send_only, media.tf's app_media_upload) - given directly to the EC2 instance's OWN role
# instead. The app's AWS SDK clients (SnsNotificationPublisher, S3MediaUploadService) both build
# with no explicit credentials provider, so they already use the SDK's default credential chain -
# which auto-discovers an EC2 instance role's temporary credentials via IMDS with zero app code or
# env var changes. That's strictly better than the alternative (passing the notification-service
# user's long-lived AWS_ACCESS_KEY_ID/SECRET as plain Beanstalk env vars, the way Render had to,
# since Render isn't AWS and has no instance-role equivalent) - no long-lived key sitting in env
# vars at all, nothing to rotate, nothing to leak.
resource "aws_iam_role_policy" "beanstalk_ec2_sns_publish" {
  name = "${var.project}-beanstalk-sns-publish"
  role = aws_iam_role.beanstalk_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish"]
      Resource = [aws_sns_topic.notifications.arn]
    }]
  })
}

resource "aws_iam_role_policy" "beanstalk_ec2_media_upload" {
  name = "${var.project}-beanstalk-media-upload"
  role = aws_iam_role.beanstalk_ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject"]
      Resource = "${aws_s3_bucket.post_media.arn}/posts/*"
    }]
  })
}

resource "aws_iam_instance_profile" "beanstalk_ec2" {
  name = "${var.project}-beanstalk-ec2"
  role = aws_iam_role.beanstalk_ec2.name
}

resource "aws_elastic_beanstalk_environment" "main" {
  name                = "${var.project}-app-env"
  application         = aws_elastic_beanstalk_application.main.name
  solution_stack_name = "64bit Amazon Linux 2023 v4.13.6 running Docker"
  cname_prefix        = local.beanstalk_cname_prefix

  # No version_label here on purpose, as of the GitHub Actions workflow (.github/workflows/
  # deploy.yml) taking over routine deploys - that workflow calls elasticbeanstalk:UpdateEnvironment
  # directly on every push to master, entirely outside Terraform. If this attribute stayed wired
  # to aws_elastic_beanstalk_application_version.main.name (which only changes when someone
  # locally reruns package-beanstalk.sh + terraform apply), the NEXT unrelated infra-only
  # `terraform apply` would see "drift" against whatever CI most recently deployed and roll the
  # live environment back to a stale local build - actively undoing CI's deploy. Leaving this
  # unset means Terraform stops caring which version is live; aws_s3_object.beanstalk_bundle and
  # aws_elastic_beanstalk_application_version.main above still exist as a manual/fallback deploy
  # path (run package-beanstalk.sh, terraform apply, then a manual `aws elasticbeanstalk
  # update-environment --version-label ...` if CI is ever unavailable) - they just no longer
  # auto-attach to the environment themselves.

  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "EnvironmentType"
    value     = "SingleInstance"
  }

  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "ServiceRole"
    value     = aws_iam_role.beanstalk_service.name
  }

  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "InstanceType"
    value     = "t3.micro"
  }

  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "IamInstanceProfile"
    value     = aws_iam_instance_profile.beanstalk_ec2.name
  }

  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "SecurityGroups"
    value     = aws_security_group.app_instance.id
  }

  setting {
    namespace = "aws:ec2:vpc"
    name      = "VPCId"
    value     = data.aws_vpc.default.id
  }

  setting {
    namespace = "aws:ec2:vpc"
    name      = "Subnets"
    value     = join(",", data.aws_subnets.default.ids)
  }

  setting {
    namespace = "aws:ec2:vpc"
    name      = "AssociatePublicIpAddress"
    value     = "true"
  }

  # --- App env vars - same 3-value DATASOURCE_* shape application.properties already expects,
  # just pointed at the new RDS instance instead of Render's Postgres. Notifications (SNS) and
  # media upload (S3) are turned on below too, now that the core app + database have been proven
  # working - credentials for both come from the instance role above, not env vars. ---
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DATASOURCE_URL"
    value     = "jdbc:postgresql://${aws_db_instance.main.endpoint}/bodhsea"
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DATASOURCE_USERNAME"
    value     = aws_db_instance.main.username
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DATASOURCE_PASSWORD"
    value     = random_password.db_master.result
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "APP_BASE_URL"
    value     = local.beanstalk_app_base_url
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SENDGRID_API_KEY"
    value     = var.sendgrid_api_key
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SENDGRID_FROM_EMAIL"
    value     = var.sendgrid_from_email
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "AWS_REGION"
    value     = var.aws_region
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "AWS_SQS_ENABLED"
    value     = "true"
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "AWS_SNS_TOPIC_ARN"
    value     = aws_sns_topic.notifications.arn
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "AWS_MEDIA_ENABLED"
    value     = "true"
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "AWS_MEDIA_BUCKET"
    value     = aws_s3_bucket.post_media.bucket
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    # No Cloudflare/CDN in front yet (media.tf's file comment - CloudFront was refused on this
    # account, Cloudflare needs a domain that hasn't been bought yet), so this points straight at
    # S3's own regional endpoint for now - functional today, a one-env-var swap later once a CDN
    # exists in front of the bucket.
    name  = "AWS_MEDIA_CDN_DOMAIN"
    value = aws_s3_bucket.post_media.bucket_regional_domain_name
  }
}
