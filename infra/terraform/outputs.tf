output "sns_topic_arn" {
  description = "The one thing the Spring Boot app now needs (AWS_SNS_TOPIC_ARN on Render) - it publishes here once per notification, and each queue's own subscription filter policy (sns.tf) decides which of the 3 actually receive it. The app no longer talks to any queue URL directly."
  value       = aws_sns_topic.notifications.arn
}

output "queue_urls" {
  description = "SQS queue URLs - informational/debugging only now (checking queue depth, DLQ contents, etc.) - the app itself no longer publishes to these directly, see sns_topic_arn."
  value       = { for c, q in aws_sqs_queue.main : c => q.id }
}

output "queue_arns" {
  value = { for c, q in aws_sqs_queue.main : c => q.arn }
}

output "dlq_urls" {
  description = "Where to look when something ends up dead-lettered."
  value       = { for c, q in aws_sqs_queue.dlq : c => q.id }
}

output "lambda_function_names" {
  value = { for c, f in aws_lambda_function.worker : c => f.function_name }
}
