# --- Permissions for the Spring Boot app (the PRODUCER side) ---
#
# The IAM user itself (bodhsea-notification-service) was created by hand in the console before
# this Terraform existed - deliberately not imported/managed here, so `terraform destroy` can
# never accidentally delete the access-key credentials the running app depends on. Terraform
# only manages the POLICY and adds that existing user to a GROUP, per AWS's own recommended
# pattern (attach permissions to groups, not users directly).

data "aws_iam_user" "app_user" {
  user_name = "bodhsea-notification-service"
}

resource "aws_iam_group" "app_group" {
  name = "${var.project}-app"
}

resource "aws_iam_group_policy" "app_send_only" {
  name  = "${var.project}-send-only"
  group = aws_iam_group.app_group.id

  # Publish ONLY, scoped to exactly this one SNS topic - the app no longer talks to any of the
  # 3 SQS queues directly (see sns.tf: it publishes once to the topic, and each queue's own
  # subscription filter policy decides whether that message reaches it), so the app's own
  # credential doesn't need sqs:SendMessage/GetQueueUrl at all anymore. Same "leaked credential
  # can spam this ONE thing at worst" reasoning this policy already had when it was scoped to
  # the 3 queues directly - just one topic instead of three queues now.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish"]
      Resource = [aws_sns_topic.notifications.arn]
    }]
  })
}

resource "aws_iam_user_group_membership" "app_user_membership" {
  user   = data.aws_iam_user.app_user.user_name
  groups = [aws_iam_group.app_group.name]
}

# --- Permissions for the Lambda workers (the CONSUMER side) ---
#
# One execution role per channel rather than one shared role - email-worker's role can only
# ever touch the email queue/DLQ, push-worker's only the push queue/DLQ, and so on. A bug or
# compromise in one worker can't reach across into another channel's queue.

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "worker" {
  for_each = toset(local.channels)

  name               = "${var.project}-${each.key}-worker-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "worker_logs" {
  for_each = toset(local.channels)

  role       = aws_iam_role.worker[each.key].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# inapp-worker only - it's the one channel VPC-attached (lambda.tf, to reach RDS), and a
# VPC-attached Lambda needs ec2:CreateNetworkInterface/DescribeNetworkInterfaces/
# DeleteNetworkInterface to manage its own ENI in that VPC, which AWSLambdaBasicExecutionRole
# above doesn't grant. This managed policy is a superset of that one (same log permissions plus
# the ENI ones) - both attached is redundant on the logs half, not conflicting.
resource "aws_iam_role_policy_attachment" "worker_vpc_access" {
  role       = aws_iam_role.worker["inapp"].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy" "worker_sqs" {
  for_each = toset(local.channels)

  name = "${var.project}-${each.key}-sqs-consume"
  role = aws_iam_role.worker[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ]
      Resource = aws_sqs_queue.main[each.key].arn
    }]
  })
}
