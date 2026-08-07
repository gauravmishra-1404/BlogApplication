# No VPC config on any of these three - deliberately. All three need plain internet egress
# (SendGrid's API, FCM's API, and Render's public Postgres endpoint are all reached over the
# open internet, none of them live inside this AWS account's network), and a Lambda with no
# VPC attached gets that for free. Putting these in a VPC would need a NAT gateway just to
# reach the same public internet - real recurring cost for zero benefit here.
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

  environment {
    variables = each.key == "email" ? {
      SENDGRID_API_KEY    = var.sendgrid_api_key
      SENDGRID_FROM_EMAIL = var.sendgrid_from_email
      APP_BASE_URL        = var.app_base_url
      } : each.key == "push" ? {
      FCM_SERVICE_ACCOUNT_JSON = var.fcm_service_account_json
      } : {
      DATABASE_URL      = var.database_url
      DATABASE_USERNAME = var.database_username
      DATABASE_PASSWORD = var.database_password
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
