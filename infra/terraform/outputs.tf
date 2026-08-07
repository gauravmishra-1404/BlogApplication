output "queue_urls" {
  description = "SQS queue URLs the Spring Boot app publishes to - copy these into Render's environment variables (SQS_EMAIL_QUEUE_URL / SQS_PUSH_QUEUE_URL / SQS_INAPP_QUEUE_URL)."
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
