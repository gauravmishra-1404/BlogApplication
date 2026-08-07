package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.payloads.NotificationEvent;
import com.BlogApplication.Blog.services.NotificationPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
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
            // Explicit httpClientBuilder - without this, the SDK falls back to its default sync
            // client (Apache HttpClient), which is deliberately excluded from this project's
            // pom.xml (see that dependency's own comment: a classpath version conflict with
            // cloudinary-http44's own bundled httpclient causes a NoSuchMethodError at runtime).
            // UrlConnectionHttpClient needs no Apache HttpComponents at all.
            sqsClient = SqsClient.builder()
                    .region(Region.of(region))
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();
        }
        return sqsClient;
    }

    @Override
    public void publish(NotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", System.currentTimeMillis()); // correlation id for logs only - the real row id is assigned by inapp-worker's own INSERT
        payload.put("recipientUserId", event.getRecipient().getId());
        payload.put("recipientEmail", event.getRecipient().getEmail());
        payload.put("recipientDeviceToken", null); // no device-token registration built yet - see below, push is skipped entirely for now rather than sent with a token that's always null
        payload.put("type", event.getType());
        payload.put("actorName", event.getActorName());
        payload.put("title", event.getTitle());
        payload.put("body", event.getBody());
        payload.put("targetUrl", event.getTargetUrl());
        // Instant, not LocalDateTime - a bare LocalDateTime.toString() has no timezone
        // information at all, and inapp-worker (a separate JVM, running in AWS rather than on
        // this app server) needs an unambiguous instant to parse back into a real timestamp.
        // Matches InAppWorkerHandler's own Instant.parse() on the receiving end - keep both
        // sides in sync if this ever changes.
        payload.put("createdAt", Instant.now().toString());

        try {
            String json = MAPPER.writeValueAsString(payload);
            sendIfConfigured(emailQueueUrl, json, "email");
            // Skipped, not "sent and let push-worker no-op" - recipientDeviceToken above is
            // unconditionally null (no device-token registration feature exists yet), so a
            // push-queue message right now can NEVER do anything but burn a Lambda invocation
            // on an automatic no-op, for every single notification, forever. This isn't a
            // per-notification-type routing decision - it's simply "this channel has nothing to
            // do yet." Delete this skip (and just call sendIfConfigured(pushQueueUrl, ...) like
            // the other two) once device tokens are a real, populated field above.
            log.debug("Skipping push notification - no device-token registration exists yet");
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
