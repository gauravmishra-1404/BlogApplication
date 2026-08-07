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

  # SendMessage ONLY, scoped to exactly these 3 queue ARNs - the app produces notifications, it
  # never needs to receive/delete/consume from these queues (that's the Lambda workers' job,
  # via their own separate execution roles below), so it gets none of those permissions. This
  # is the actual point of doing this instead of attaching AmazonSQSFullAccess: a leaked app
  # credential can spam these 3 queues at worst, not read/drain/delete them or touch any other
  # queue in the account.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
      Resource = [for q in aws_sqs_queue.main : q.arn]
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
