package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.payloads.NotificationEvent;
import com.BlogApplication.Blog.services.NotificationPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Active in production once AWS is actually set up (aws.sqs.enabled=true) - publishes one JSON
// message per applicable channel to its own SQS queue, matching exactly the shape each Lambda
// worker under infra/lambdas expects (see any of their NotificationMessage.java - field names
// must stay in sync by hand across all 4 copies of that shape: this class's payload map, and
// the 3 Lambda-side POJOs).
@Service
@ConditionalOnProperty(prefix = "aws.sqs", name = "enabled", havingValue = "true")
public class SqsNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsNotificationPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // @Value - a Spring annotation that injects a single property's value straight into a
    // field, read from application.properties (or an environment variable, since Spring
    // treats env vars as just another property source) at bean-creation time. The
    // ${property.name:default} syntax means "use this default if the property was never set" -
    // same convention already used throughout application.properties (e.g. sendgrid.api-key).
    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.sqs.email-queue-url:}")
    private String emailQueueUrl;

    @Value("${aws.sqs.push-queue-url:}")
    private String pushQueueUrl;

    @Value("${aws.sqs.inapp-queue-url:}")
    private String inappQueueUrl;

    // Credentials are picked up automatically by AWS SDK v2's default credential chain -
    // AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY environment variables first, same two values
    // generated for the bodhsea-notification-service IAM user - nothing to configure here by
    // hand, the SDK finds them itself.
    private SqsClient sqsClient;

    private SqsClient client() {
        if (sqsClient == null) {
            sqsClient = SqsClient.builder().region(Region.of(region)).build();
        }
        return sqsClient;
    }

    @Override
    public void publish(NotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", System.currentTimeMillis()); // correlation id for logs only - the real row id is assigned by inapp-worker's own INSERT
        payload.put("recipientUserId", event.getRecipient().getId());
        payload.put("recipientEmail", event.getRecipient().getEmail());
        payload.put("recipientDeviceToken", null); // no device-token registration built yet - push-worker no-ops on a null token
        payload.put("type", event.getType());
        payload.put("actorName", event.getActorName());
        payload.put("title", event.getTitle());
        payload.put("body", event.getBody());
        payload.put("targetUrl", event.getTargetUrl());
        payload.put("createdAt", LocalDateTime.now().toString());

        try {
            String json = MAPPER.writeValueAsString(payload);
            sendIfConfigured(emailQueueUrl, json, "email");
            sendIfConfigured(pushQueueUrl, json, "push");
            sendIfConfigured(inappQueueUrl, json, "inapp");
        } catch (Exception e) {
            // Never let a notification failure break whatever real action triggered it (a
            // follow, a comment) - same "log it, don't propagate" tolerance the rest of this
            // project already applies to every optional/best-effort integration.
            log.error("Failed to publish notification event: {}", e.getMessage());
        }
    }

    private void sendIfConfigured(String queueUrl, String json, String channel) {
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("Skipping {} notification - queue URL not configured", channel);
            return;
        }
        try {
            client().sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(json)
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish {} notification to SQS: {}", channel, e.getMessage());
        }
    }
}
