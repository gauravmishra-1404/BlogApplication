package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Notification;
import com.BlogApplication.Blog.payloads.NotificationEvent;
import com.BlogApplication.Blog.repositories.NotificationRepo;
import com.BlogApplication.Blog.services.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// Active whenever aws.sqs.enabled is false or unset - i.e. local dev, and production too until
// the AWS side (Terraform) is actually applied and AWS_ACCESS_KEY_ID/SECRET are set. Mirrors
// ConsoleEmailService's own reasoning: the in-app channel is genuinely simple enough to just do
// right here (one INSERT into the SAME database this app already has a connection to - no
// queue/Lambda round-trip buys anything for that specific channel), while email/push get a
// dev-stub log line instead of a real send, same "log what would have happened" pattern
// ConsoleEmailService/ConsoleSmsService already use.
//
// @ConditionalOnProperty - a Spring Boot annotation that only registers this class as a bean
// (i.e. only creates an instance of it at all) when the given property matches. Here:
// aws.sqs.enabled=false (or the property is missing entirely, matchIfMissing=true) - Spring
// checks this once at startup, not per-request. SqsNotificationPublisher declares the opposite
// condition (havingValue="true"), so exactly one of the two ever exists in the running app.
@Service
@ConditionalOnProperty(prefix = "aws.sqs", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationPublisher.class);

    @Autowired
    private NotificationRepo notificationRepo;

    @Override
    public void publish(NotificationEvent event) {
        Notification notification = new Notification();
        notification.setRecipient(event.getRecipient());
        notification.setType(event.getType());
        notification.setActorName(event.getActorName());
        notification.setTitle(event.getTitle());
        notification.setBody(event.getBody());
        notification.setTargetUrl(event.getTargetUrl());
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepo.save(notification);

        log.info("[DEV NOTIFICATION STUB] Would email {}: {}", event.getRecipient().getEmail(), event.getTitle());
        log.info("[DEV NOTIFICATION STUB] Would push-notify {} (no device token wiring yet): {}",
                event.getRecipient().getEmail(), event.getTitle());
    }
}
