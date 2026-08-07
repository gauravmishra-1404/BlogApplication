package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.NotificationEvent;

// Two implementations, exactly one active at a time depending on whether AWS is configured -
// SnsNotificationPublisher (publishes once to the SNS topic, which fans out to the 3 SQS queues
// per infra/terraform/sns.tf's filter policies, once Terraform is applied) and
// LocalNotificationPublisher (writes the in-app row directly, dev-stub logs for email/push) -
// same shape as EmailService's own SendGridEmailService/ConsoleEmailService split.
// NotificationService (the class the rest of the app actually calls) doesn't know or care which
// one it got - Spring wires in whichever one is active, see @ConditionalOnProperty on each
// implementation.
public interface NotificationPublisher {
    void publish(NotificationEvent event);
}
