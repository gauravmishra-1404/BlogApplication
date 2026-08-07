# The pub/sub layer from the original hand-drawn architecture, sitting between the app and the
# 3 SQS queues - the app now publishes ONE message per notification event, and this topic (via
# each subscription's own filter policy below) decides which of the 3 queues actually receive
# it, instead of the app deciding that itself with a manual if/skip per channel (which is what
# SqsNotificationPublisher did before this file existed - see its replacement,
# SnsNotificationPublisher, for the app-side half of this change).
#
# Routing is keyed on per-message boolean ATTRIBUTES the publisher sets (channel_email,
# channel_push, channel_inapp), not on notification TYPE. This was a deliberate choice over a
# type-keyed filter policy: which channels apply to a given event is a fact the publisher
# already has to compute anyway (e.g. "does this recipient even have a push token yet?"), and a
# type-keyed policy would need editing here in Terraform every time a new notification type is
# added on the app side - two places to keep in sync instead of one. An attribute-keyed policy
# only needs a new "channel_x" attribute value from the publisher; this file never changes again
# as new notification types show up.
resource "aws_sns_topic" "notifications" {
  name = "${var.project}-events"
}

resource "aws_sns_topic_subscription" "worker" {
  for_each = toset(local.channels)

  topic_arn = aws_sns_topic.notifications.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.main[each.key].arn

  # Without this, SNS wraps the published message in its own JSON envelope (Message/MessageId/
  # TopicArn/... fields, with the actual payload as a STRING inside "Message") before handing it
  # to SQS - every Lambda worker's MAPPER.readValue(message.getBody(), NotificationMessage.class)
  # would then fail to parse it, since the top-level JSON wouldn't match NotificationMessage's
  # shape anymore. raw_message_delivery=true makes SNS deliver exactly the bytes the app
  # published, so none of the 3 Lambda handlers needed any change for this SNS layer to exist.
  raw_message_delivery = true

  filter_policy = jsonencode({
    "channel_${each.key}" = ["true"]
  })
}

# SNS needs explicit permission to deliver INTO each queue - without this policy attached to the
# queue itself, AWS doesn't know this specific topic is allowed to call sqs:SendMessage on it.
# Scoped with a SourceArn condition to exactly this topic, not "any SNS topic in the account".
resource "aws_sqs_queue_policy" "allow_sns_publish" {
  for_each = toset(local.channels)

  queue_url = aws_sqs_queue.main[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.main[each.key].arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_sns_topic.notifications.arn }
      }
    }]
  })
}
