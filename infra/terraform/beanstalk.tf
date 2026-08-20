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
  # Switches to the real HTTPS domain automatically once var.domain_name is set (https.tf) -
  # every place this local feeds (APP_BASE_URL below, email-worker's own copy in lambda.tf,
  # media.tf's CORS allowed_origins) picks up the upgrade in the same apply, nothing left
  # pointing at the old raw http:// elasticbeanstalk.com URL by hand afterward.
  beanstalk_app_base_url = local.https_enabled ? "https://${var.domain_name}" : "http://${local.beanstalk_cname_prefix}.${var.aws_region}.elasticbeanstalk.com"

  # Same "Terraform uploads an already-built artifact, doesn't build it itself" convention
  # lambda.tf already uses for the worker jars - run infra/terraform/package-beanstalk.sh before
  # `terraform apply`. It runs the Maven build itself (on whatever machine runs the script) and
  # stages only a pre-built jar + a lean runtime-only Dockerfile.deploy - deliberately NOT the
  # repo's own root Dockerfile's multi-stage approach (which compiles ON the target instance): a
  # real deploy did that and overwhelmed the target t3.micro (1GB RAM) badly enough to make it
  # stop responding entirely mid-build. See package-beanstalk.sh's own comment for the full story.
  beanstalk_bundle = "${path.module}/build/beanstalk-app.zip"

  # Feeds the "lb" environment below - kept as its own local (rather than inlined directly into
  # that resource) from when there were briefly two environment resources sharing it during the
  # blue-green cutover to LoadBalanced/HTTPS (see that resource's own comment); left as a local
  # since a shared block of settings is still a reasonable shape even with one consumer now.
  beanstalk_shared_settings = {
    "ServiceRole"              = { namespace = "aws:elasticbeanstalk:environment", value = aws_iam_role.beanstalk_service.name }
    "InstanceType"             = { namespace = "aws:autoscaling:launchconfiguration", value = "t3.micro" }
    "IamInstanceProfile"       = { namespace = "aws:autoscaling:launchconfiguration", value = aws_iam_instance_profile.beanstalk_ec2.name }
    "SecurityGroups"           = { namespace = "aws:autoscaling:launchconfiguration", value = aws_security_group.app_instance.id }
    "VPCId"                    = { namespace = "aws:ec2:vpc", value = data.aws_vpc.default.id }
    "Subnets"                  = { namespace = "aws:ec2:vpc", value = join(",", data.aws_subnets.default.ids) }
    "AssociatePublicIpAddress" = { namespace = "aws:ec2:vpc", value = "true" }
    "DATASOURCE_URL"           = { namespace = "aws:elasticbeanstalk:application:environment", value = "jdbc:postgresql://${aws_db_instance.main.endpoint}/bodhsea" }
    "DATASOURCE_USERNAME"      = { namespace = "aws:elasticbeanstalk:application:environment", value = aws_db_instance.main.username }
    "DATASOURCE_PASSWORD"      = { namespace = "aws:elasticbeanstalk:application:environment", value = random_password.db_master.result }
    "APP_BASE_URL"             = { namespace = "aws:elasticbeanstalk:application:environment", value = local.beanstalk_app_base_url }
    "SENDGRID_API_KEY"         = { namespace = "aws:elasticbeanstalk:application:environment", value = var.sendgrid_api_key }
    "SENDGRID_FROM_EMAIL"      = { namespace = "aws:elasticbeanstalk:application:environment", value = var.sendgrid_from_email }
    "AWS_REGION"               = { namespace = "aws:elasticbeanstalk:application:environment", value = var.aws_region }
    "AWS_SQS_ENABLED"          = { namespace = "aws:elasticbeanstalk:application:environment", value = "true" }
    "AWS_SNS_TOPIC_ARN"        = { namespace = "aws:elasticbeanstalk:application:environment", value = aws_sns_topic.notifications.arn }
    "AWS_MEDIA_ENABLED"        = { namespace = "aws:elasticbeanstalk:application:environment", value = "true" }
    "AWS_MEDIA_BUCKET"         = { namespace = "aws:elasticbeanstalk:application:environment", value = aws_s3_bucket.post_media.bucket }
    # No Cloudflare/CDN in front yet (media.tf's file comment - CloudFront was refused on this
    # account, Cloudflare needs a domain that hasn't been bought yet), so this points straight at
    # S3's own regional endpoint for now - functional today, a one-env-var swap later once a CDN
    # exists in front of the bucket.
    "AWS_MEDIA_CDN_DOMAIN" = { namespace = "aws:elasticbeanstalk:application:environment", value = aws_s3_bucket.post_media.bucket_regional_domain_name }
    # Shared session store (redis.tf) - see that file's own comment for why this exists. Redis
    # here specifically (not "none") is what actually makes it safe for the ASG below to run more
    # than 1 instance at once.
    "SPRING_SESSION_STORE_TYPE" = { namespace = "aws:elasticbeanstalk:application:environment", value = "redis" }
    "SPRING_DATA_REDIS_HOST"    = { namespace = "aws:elasticbeanstalk:application:environment", value = aws_elasticache_cluster.sessions.cache_nodes[0].address }
    "SPRING_DATA_REDIS_PORT"    = { namespace = "aws:elasticbeanstalk:application:environment", value = tostring(aws_elasticache_cluster.sessions.cache_nodes[0].port) }
  }
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
      Effect = "Allow"
      Action = ["s3:PutObject"]
      Resource = [
        "${aws_s3_bucket.post_media.arn}/posts/*",
        "${aws_s3_bucket.post_media.arn}/profiles/*", # avatar/cover uploads - see media.tf
      ]
    }]
  })
}

resource "aws_iam_instance_profile" "beanstalk_ec2" {
  name = "${var.project}-beanstalk-ec2"
  role = aws_iam_role.beanstalk_ec2.name
}

# The original environment (SingleInstance, no ALB, no HTTPS) has been decommissioned - it was
# replaced by the LoadBalanced environment below via a blue-green cutover (a second, separate
# environment created alongside it, verified healthy, then swap-environment-cnames moved the real
# "bodhsea-app" hostname over to it) rather than an in-place conversion, because Beanstalk rejects
# EnvironmentType/LoadBalancerType changes on an existing environment outright
# (ConfigurationValidationException, confirmed live, no partial change applied).
#
# cname_prefix below is local.beanstalk_cname_prefix ("bodhsea-app"), NOT "<prefix>-lb" - real,
# hard lesson: swap-environment-cnames changes the actual live cname_prefix on both environments
# (that's the whole mechanism), and cname_prefix can't be updated in place (ForceNew) - leaving
# this declared as the pre-swap "-lb" value would have left Terraform's own config out of sync
# with the swap that already happened, and its "fix" for that drift is to destroy and recreate
# this environment with the OLD prefix, undoing the swap and taking the live site down to do it.
# Confirmed via a real `terraform plan` showing exactly that "must be replaced" action before this
# was corrected. Bumping cname_prefix here always needs to mirror whatever's actually true after
# the last real swap, not what this environment was created with.
#
# No version_label here, same as "main" - deliberately, since CI (.github/workflows/deploy.yml)
# manages deploys directly via UpdateEnvironment from here on, matching "main"'s own reasoning.
# BUT: a environment with no version_label starts on AWS's own placeholder "Welcome to Elastic
# Beanstalk" sample app, not your actual app - confirmed live, the domain briefly served that
# placeholder instead of Bodh Sea right after this environment's first creation, until a one-time
# manual `aws elasticbeanstalk update-environment --version-label <current>` bootstrapped the
# real app onto it. A brand-new environment created this way always needs that one manual step;
# it's not something Terraform or CI does for you on environment creation itself.
resource "aws_elastic_beanstalk_environment" "lb" {
  count               = local.https_enabled ? 1 : 0
  name                = "${var.project}-app-env-lb"
  application         = aws_elastic_beanstalk_application.main.name
  solution_stack_name = "64bit Amazon Linux 2023 v4.13.6 running Docker"
  cname_prefix        = local.beanstalk_cname_prefix

  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "EnvironmentType"
    value     = "LoadBalanced"
  }

  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "LoadBalancerType"
    value     = "application"
  }

  # The ALB target group's health check defaults to path "/", expecting HTTP 200 - but "/" is
  # behind Spring Security here, so an unauthenticated health-check request gets redirected to
  # /login (302), not 200. The target group's default healthy-codes list is just "200", so it
  # marked every instance unhealthy (Target.ResponseCodeMismatch) even though the app itself was
  # fully up the whole time - confirmed live, curl against /login and /registerUser both returned
  # 200 directly while this environment showed Health: Red. /login is the one page this app
  # already has as .permitAll() (SecurityConfig.java) that returns a real 200 with no auth, so it
  # doubles as a correct, zero-extra-code health-check target - no actuator dependency in this
  # project to add a dedicated /actuator/health endpoint instead.
  setting {
    namespace = "aws:elasticbeanstalk:environment:process:default"
    name      = "HealthCheckPath"
    value     = "/login"
  }

  dynamic "setting" {
    for_each = local.beanstalk_shared_settings
    content {
      namespace = setting.value.namespace
      name      = setting.key
      value     = setting.value.value
    }
  }

  # HTTPS listener on the ALB - port 80 is left enabled too (Beanstalk's own default listener),
  # deliberately no forced redirect to 443 yet - existing http:// links/bookmarks (including
  # every screenshot/link shared this whole build) keep working unchanged; a redirect is a
  # reasonable follow-up once the domain's been live a while, not bundled into this migration.
  setting {
    namespace = "aws:elbv2:listener:443"
    name      = "ListenerEnabled"
    value     = "true"
  }

  setting {
    namespace = "aws:elbv2:listener:443"
    name      = "Protocol"
    value     = "HTTPS"
  }

  setting {
    namespace = "aws:elbv2:listener:443"
    name      = "SSLCertificateArns"
    value     = aws_acm_certificate_validation.main[0].certificate_arn
  }
}
