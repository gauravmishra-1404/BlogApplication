# Shorts Phase 2 - automatic video transcoding. Not a 4th entry in local.channels (sqs.tf/iam.tf/
# lambda.tf/s3.tf) - those three are Spring Boot's own notification fan-out (one event -> up to 3
# messages), a fundamentally different trigger shape from this: S3 itself publishes directly to
# this queue the moment a Short's raw video finishes uploading, no app-side publish step at all.
# Kept as its own standalone set of resources instead, mirroring the SAME per-worker conventions
# (own queue+DLQ, own dedicated IAM role, own Lambda function) established by the channels above.
#
# Engine: ffmpeg running inside the Lambda itself (via a public Lambda Layer), not AWS Elemental
# MediaConvert - confirmed with the user after a real cost comparison (MediaConvert bills per
# output-minute regardless of compute used; ffmpeg-in-Lambda stays inside Lambda's own permanent
# free tier at this app's realistic volume, roughly 20x cheaper at any real volume). One Lambda,
# one invocation per video - same synchronous-single-worker shape lambda.tf's 3 existing workers
# already use, not a second Lambda plus an EventBridge completion rule MediaConvert would need.
#
# Result gets back onto the row via plain JDBC (same DATABASE_URL/USERNAME/PASSWORD env-var
# pattern inapp-worker already uses), not a callback/webhook - there's no inbound Lambda-to-app
# channel anywhere in this project, and inapp-worker's own "Lambda talks straight to Postgres"
# precedent already covers this without inventing one.

locals {
  transcode_worker_jar     = "${path.module}/../lambdas/transcode-worker/target/transcode-worker-1.0.0.jar"
  transcode_worker_handler = "com.bodhsea.notifications.transcode.TranscodeWorkerHandler::handleRequest"
}

# --- Queue + DLQ (same shape as sqs.tf's per-channel queues) ---

resource "aws_sqs_queue" "transcode_shorts_dlq" {
  name                      = "${var.project}-transcode-shorts-dlq"
  message_retention_seconds = 1209600 # 14 days (the max) - same reasoning as the notification DLQs
}

resource "aws_sqs_queue" "transcode_shorts" {
  name = "${var.project}-transcode-shorts"

  # Well above the existing 60s notification queues' visibility timeout - a 1-2 minute video's
  # ffmpeg pass plus S3 download/upload genuinely needs minutes, not seconds, before SQS should
  # consider the message abandoned and retry it.
  visibility_timeout_seconds = 360
  message_retention_seconds  = 345600 # 4 days

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.transcode_shorts_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "transcode_shorts_dlq_allow" {
  queue_url = aws_sqs_queue.transcode_shorts_dlq.id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.transcode_shorts.arn]
  })
}

# Required, easy-to-miss piece of any S3-to-SQS event notification - without this, S3 is refused
# permission to publish INTO the queue at all (a queue's own IAM role/policy has no say over who
# else can send to it; this is that grant, scoped to only this one bucket via aws:SourceArn).
resource "aws_sqs_queue_policy" "transcode_shorts_s3_publish" {
  queue_url = aws_sqs_queue.transcode_shorts.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "s3.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.transcode_shorts.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_s3_bucket.post_media.arn }
      }
    }]
  })
}

# The first S3 event notification anywhere in this Terraform config - fires the moment a raw
# Short video finishes uploading (filtered to the shorts/ prefix only, so Post/profile-image
# uploads under the same bucket never touch this pipeline at all).
resource "aws_s3_bucket_notification" "post_media_shorts_uploaded" {
  bucket = aws_s3_bucket.post_media.id

  queue {
    queue_arn     = aws_sqs_queue.transcode_shorts.arn
    events        = ["s3:ObjectCreated:*"]
    filter_prefix = "shorts/"
  }

  depends_on = [aws_sqs_queue_policy.transcode_shorts_s3_publish]
}

# --- IAM: its own dedicated role, not part of iam.tf's per-channel aws_iam_role.worker map -
# same "one execution role per worker, scoped to only what it needs" principle that file's own
# comment already states, just for a worker that isn't one of the 3 notification channels. ---

resource "aws_iam_role" "transcode_worker" {
  name               = "${var.project}-transcode-worker-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "transcode_worker_logs" {
  role       = aws_iam_role.transcode_worker.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# VPC-attached (needs RDS) - same reasoning iam.tf's own worker_vpc_access comment gives for
# inapp-worker.
resource "aws_iam_role_policy_attachment" "transcode_worker_vpc_access" {
  role       = aws_iam_role.transcode_worker.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "transcode_worker_sqs" {
  name = "${var.project}-transcode-worker-sqs-consume"
  role = aws_iam_role.transcode_worker.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ]
      Resource = aws_sqs_queue.transcode_shorts.arn
    }]
  })
}

# The first Lambda in this project that reads/writes S3 object BYTES itself (the 3 notification
# workers only ever publish messages; S3MediaUploadService on the app side only ever presigns
# URLs, never touches bytes either) - read the raw upload, write the normalized output + thumbnail
# to their own new prefixes. Scoped tight, same least-privilege pattern as media.tf's own
# app_media_upload policy.
resource "aws_iam_role_policy" "transcode_worker_s3" {
  name = "${var.project}-transcode-worker-s3"
  role = aws_iam_role.transcode_worker.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = ["${aws_s3_bucket.post_media.arn}/shorts/*"]
      },
      {
        Effect = "Allow"
        Action = ["s3:PutObject"]
        Resource = [
          "${aws_s3_bucket.post_media.arn}/shorts-transcoded/*",
          "${aws_s3_bucket.post_media.arn}/shorts-thumbnails/*",
        ]
      }
    ]
  })
}

# --- The Lambda itself ---

# Same no-ingress/wide-egress shape as lambda.tf's aws_security_group.lambda_inapp - this worker
# only ever calls OUT (S3, RDS), nothing ever needs to reach IN to it.
resource "aws_security_group" "lambda_transcode" {
  name        = "${var.project}-lambda-transcode"
  description = "VPC attachment for the transcode-worker Lambda, so it can reach RDS"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_s3_object" "transcode_worker_jar" {
  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "transcode-worker-1.0.0.jar"
  source = local.transcode_worker_jar
  etag   = filemd5(local.transcode_worker_jar)
}

resource "aws_lambda_function" "transcode_worker" {
  function_name = "${var.project}-transcode-worker"
  role          = aws_iam_role.transcode_worker.arn

  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = aws_s3_object.transcode_worker_jar.key
  source_code_hash = filebase64sha256(local.transcode_worker_jar)

  runtime = "java21"
  handler = local.transcode_worker_handler
  layers  = var.ffmpeg_layer_arn != "" ? [var.ffmpeg_layer_arn] : []

  # Heavier than the 512MB notification workers (video work, not a quick API call/DB insert) -
  # Lambda allocates CPU proportional to memory, so this also buys faster ffmpeg encode time, not
  # just headroom against OOM on a larger input file.
  memory_size = 2048
  # Generous relative to the ~1-2 minute Shorts this pipeline actually handles (client-side cap,
  # see composeModal.js's SHORT_VIDEO_MAX_BYTES) - well under Lambda's 900s hard ceiling.
  timeout = 300

  # Default 512MB /tmp isn't enough to hold the raw input + normalized output + thumbnail
  # together for anything but a tiny clip.
  ephemeral_storage {
    size = 2048
  }

  vpc_config {
    subnet_ids         = data.aws_subnets.default.ids
    security_group_ids = [aws_security_group.lambda_transcode.id]
  }

  environment {
    variables = {
      DATABASE_URL      = "jdbc:postgresql://${aws_db_instance.main.endpoint}/bodhsea"
      DATABASE_USERNAME = aws_db_instance.main.username
      DATABASE_PASSWORD = random_password.db_master.result
      MEDIA_BUCKET      = aws_s3_bucket.post_media.id
      MEDIA_CDN_DOMAIN  = aws_s3_bucket.post_media.bucket_regional_domain_name
    }
  }
}

resource "aws_lambda_event_source_mapping" "transcode_worker_trigger" {
  event_source_arn = aws_sqs_queue.transcode_shorts.arn
  function_name    = aws_lambda_function.transcode_worker.arn

  # 1, not the notification workers' 10 - one video's transcode work is heavy enough per-message
  # that batching several into a single invocation risks that invocation's own timeout, not saving
  # meaningful cold-start overhead the way it does for a quick API call/DB insert.
  batch_size              = 1
  function_response_types = ["ReportBatchItemFailures"]
}
