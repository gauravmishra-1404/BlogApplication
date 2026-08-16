# email/push stay outside any VPC - deliberately. Both need plain internet egress (SendGrid's API,
# FCM's API), neither talks to the database, and a Lambda with no VPC attached gets that internet
# access for free. inapp-worker is the one exception: it writes directly to Postgres, which is now
# RDS (rds.tf) sitting inside the default VPC with publicly_accessible=false - so unlike when this
# was Render's public endpoint, inapp-worker now genuinely needs to be inside that same VPC to
# reach it at all (see aws_security_group.lambda_inapp and its vpc_config block below). email/push
# staying VPC-free avoids paying for a NAT gateway they'd otherwise need to keep their own internet
# access once inside a VPC - only inapp-worker's traffic (SQS trigger + RDS, both reachable without
# a NAT/internet route) needs that trade-off.
#
# One real cost of that trade-off: a VPC-attached Lambda's outbound traffic - including its OWN
# CloudWatch Logs delivery - goes through its VPC ENI, not some separate out-of-band channel. With
# no NAT gateway and no VPC interface endpoint for `logs` here (both are real recurring cost, ~$7-
# 45/month depending which), inapp-worker's own execution logs won't reach CloudWatch while this
# stays as-is. Its actual job (insert into notifications) is unaffected - SQS invokes it and RDS
# writes both stay reachable purely via the VPC - this only costs observability into ITS OWN logs,
# not function correctness. Add a VPC interface endpoint for `logs` later if that gap matters.
#
# Each worker is a Java 21 Lambda, packaged as a shaded (fat) jar by its own Maven build - run
# `mvn -f infra/lambdas/<channel>-worker/pom.xml package` for each BEFORE `terraform apply`,
# same as any other Java deploy artifact. Terraform just picks up the already-built jar (via S3,
# see s3.tf) and uploads it; it doesn't invoke Maven itself.

locals {
  worker_jar = {
    for c in local.channels :
    c => "${path.module}/../lambdas/${c}-worker/target/${c}-worker-1.0.0.jar"
  }
  worker_handler = {
    email = "com.bodhsea.notifications.email.EmailWorkerHandler::handleRequest"
    push  = "com.bodhsea.notifications.push.PushWorkerHandler::handleRequest"
    inapp = "com.bodhsea.notifications.inapp.InAppWorkerHandler::handleRequest"
  }
}

# No ingress - nothing ever needs to initiate a connection INTO this Lambda's ENI, only out to
# RDS. Egress wide open (not scoped to just the db SG/5432) since AWS's own default egress rule
# would otherwise be replaced with nothing at all the moment this security group is attached -
# rds.tf's aws_security_group.db is what actually restricts what inapp-worker can reach on 5432.
resource "aws_security_group" "lambda_inapp" {
  name        = "${var.project}-lambda-inapp"
  description = "VPC attachment for the inapp-worker Lambda, so it can reach RDS"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lambda_function" "worker" {
  for_each = toset(local.channels)

  function_name = "${var.project}-${each.key}-worker"
  role          = aws_iam_role.worker[each.key].arn

  # Via S3 (s3.tf), not a direct `filename` upload - see s3.tf's own comment for why (the
  # push-worker jar alone is well past Lambda's 50MB direct-upload ceiling).
  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = aws_s3_object.worker_jar[each.key].key
  source_code_hash = filebase64sha256(local.worker_jar[each.key])

  runtime = "java21"
  handler = local.worker_handler[each.key]

  # Java's own JVM startup dominates cold-start time here (can be 1-3s+ on a cold invocation,
  # a well-known Java Lambda trade-off vs. Node/Python's ~150ms) - memory_size affects allocated
  # CPU proportionally in Lambda, so 512MB (vs the 128MB default) buys back a meaningful chunk
  # of that JVM init time for a small cost-per-ms increase. Timeout well above the queue's own
  # visibility_timeout headroom to leave room for a slow cold start plus the actual API call.
  memory_size = 512
  timeout     = 45
  # Deliberately NOT using provisioned concurrency to avoid cold starts - that keeps N instances
  # warm 24/7 and bills for that idle time regardless of traffic (roughly $6-7/month per warm
  # instance at 512MB), which is exactly the "pay for uptime, not usage" trade-off EC2 had that
  # this whole stack was built to avoid. Cold starts (1-3s+) are accepted as the cost of staying
  # genuinely usage-based; add aws_lambda_provisioned_concurrency_config later, per-function, if
  # notification latency ever actually becomes a problem worth that trade-off.

  dynamic "vpc_config" {
    for_each = each.key == "inapp" ? [1] : []
    content {
      subnet_ids         = data.aws_subnets.default.ids
      security_group_ids = [aws_security_group.lambda_inapp.id]
    }
  }

  environment {
    variables = each.key == "email" ? {
      SENDGRID_API_KEY    = var.sendgrid_api_key
      SENDGRID_FROM_EMAIL = var.sendgrid_from_email
      APP_BASE_URL        = local.beanstalk_app_base_url
      } : each.key == "push" ? {
      FCM_SERVICE_ACCOUNT_JSON = var.fcm_service_account_json
      } : {
      # Points at the new RDS instance directly (same source-of-truth resources beanstalk.tf's
      # app env vars use) rather than the old var.database_url/username/password trio, which
      # pointed at Render's now-superseded Postgres - this Lambda's DB target and the main app's
      # DB target are the same database again, just both referencing aws_db_instance.main instead
      # of one of them lagging behind on a stale variable.
      DATABASE_URL      = "jdbc:postgresql://${aws_db_instance.main.endpoint}/bodhsea"
      DATABASE_USERNAME = aws_db_instance.main.username
      DATABASE_PASSWORD = random_password.db_master.result
    }
  }
}

resource "aws_lambda_event_source_mapping" "worker_trigger" {
  for_each = toset(local.channels)

  event_source_arn = aws_sqs_queue.main[each.key].arn
  function_name    = aws_lambda_function.worker[each.key].arn

  # Process a handful of notifications per invocation instead of one-at-a-time - cuts down on
  # cold starts under any real burst (e.g. a popular post getting a wave of comments at once)
  # without meaningfully hurting per-notification latency at this volume. Matters more here than
  # it would for Node/Python, since each cold Java invocation is the expensive one to avoid.
  batch_size                         = 10
  maximum_batching_window_in_seconds = 5
  function_response_types            = ["ReportBatchItemFailures"]
}
