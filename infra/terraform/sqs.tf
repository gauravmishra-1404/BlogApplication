# Three channels (email / push / in-app), each with its own queue + dead-letter queue - mirrors
# the hand-drawn architecture exactly: Spring Boot fans a single notification event out into up
# to 3 messages (one per applicable channel), each channel is consumed independently so a slow
# or failing channel (e.g. SendGrid being down) never blocks or delays the others.

locals {
  channels = ["email", "push", "inapp"]
}

resource "aws_sqs_queue" "dlq" {
  for_each = toset(local.channels)

  name = "${var.project}-${each.key}-dlq"

  # 14 days (the max) - a dead-lettered notification is exactly the kind of thing you want time
  # to notice and manually investigate/replay, not something that should silently expire in a
  # day or two while you're not looking.
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "main" {
  for_each = toset(local.channels)

  name = "${var.project}-${each.key}"

  # Give a consumer Lambda a full minute to do its work (call SendGrid/FCM, write to Postgres)
  # before SQS considers the message abandoned and makes it visible to be retried - well above
  # any of these calls' expected latency, so a slow-but-succeeding attempt is never duplicated.
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600 # 4 days

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    # After 5 failed attempts (Lambda threw / timed out / SendGrid-etc rejected), stop retrying
    # automatically and move it to the DLQ instead - the "give up and flag it" line from the
    # original sketch's DLQ box, just expressed as a redrive policy instead of a separate queue
    # your own code has to check.
    maxReceiveCount = 5
  })
}

# Lets the DLQ's own console/CLI redrive-to-source feature work (manually replaying dead
# letters back into the main queue once you've fixed whatever caused them to fail) - without
# this policy attached to the DLQ itself, AWS doesn't know which queue is allowed to redrive
# INTO it.
resource "aws_sqs_queue_redrive_allow_policy" "dlq_allow" {
  for_each = toset(local.channels)

  queue_url = aws_sqs_queue.dlq[each.key].id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.main[each.key].arn]
  })
}
