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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Active in production once AWS is actually set up (aws.sqs.enabled=true - the property name
// predates this class; it was originally SqsNotificationPublisher publishing straight to SQS,
// kept as-is rather than renamed so existing Render env vars don't need touching for what's
// really just an internal implementation swap). Publishes ONE message to the SNS topic per
// notification event - SNS itself (see infra/terraform/sns.tf) fans that out to whichever of
// the 3 SQS queues its own subscription filter policy matches, based on the channel_* message
// attributes this class sets per notification TYPE (see CHANNELS_BY_TYPE below). SNS decides
// mechanically ("does this message have attribute X") - what channels a given type actually
// wants is a business decision that belongs here, in the one place every notify() call already
// passes through, not duplicated per call site.
@Service
@ConditionalOnProperty(prefix = "aws.sqs", name = "enabled", havingValue = "true")
public class SnsNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Per-notification-type channel routing, decided here rather than at each call site
    // (FollowServiceImpl etc. just call notify() with a type string, same as always) - one
    // place to look up "what does type X actually send" as new types get added, instead of
    // that decision being scattered across every caller. NEW_FOLLOWER -> email only right now,
    // by explicit instruction (not inapp) - add entries here as each new type's channels get
    // decided; anything not listed falls back to DEFAULT_CHANNELS below rather than silently
    // going nowhere.
    private static final Map<String, Set<String>> CHANNELS_BY_TYPE = Map.of(
            "NEW_FOLLOWER", Set.of("channel_email")
    );
    private static final Set<String> DEFAULT_CHANNELS = Set.of("channel_email", "channel_inapp");

    // @Value - see LocalNotificationPublisher/SqsNotificationPublisher's own history for the
    // full explanation of this annotation; same pattern here, just resolving to the one topic
    // ARN now instead of 3 separate queue URLs.
    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.sns.topic-arn:}")
    private String topicArn;

    // Credentials picked up automatically by AWS SDK v2's default credential chain -
    // AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY environment variables, the pair generated for
    // the bodhsea-notification-service IAM user (now scoped to sns:Publish on this one topic
    // only, see infra/terraform/iam.tf) - nothing to configure here by hand.
    private SnsClient snsClient;

    private SnsClient client() {
        if (snsClient == null) {
            // Explicit httpClientBuilder - without this, the SDK falls back to its default sync
            // client (Apache HttpClient), which is deliberately excluded from this project's
            // pom.xml (see that dependency's own comment: a classpath version conflict with
            // cloudinary-http44's own bundled httpclient causes a NoSuchMethodError at runtime).
            snsClient = SnsClient.builder()
                    .region(Region.of(region))
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();
        }
        return snsClient;
    }

    @Override
    public void publish(NotificationEvent event) {
        if (topicArn == null || topicArn.isBlank()) {
            log.warn("Skipping notification publish - SNS topic ARN not configured");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", System.currentTimeMillis()); // correlation id for logs only - the real row id is assigned by inapp-worker's own INSERT
        payload.put("recipientUserId", event.getRecipient().getId());
        payload.put("recipientEmail", event.getRecipient().getEmail());
        payload.put("recipientDeviceToken", null); // no device-token registration built yet
        payload.put("type", event.getType());
        payload.put("actorName", event.getActorName());
        payload.put("title", event.getTitle());
        payload.put("body", event.getBody());
        payload.put("targetUrl", event.getTargetUrl());
        // Instant, not LocalDateTime - a bare LocalDateTime.toString() has no timezone
        // information at all, and the Lambda workers (separate JVMs, running in AWS rather than
        // on this app server) need an unambiguous instant to parse back into a real timestamp.
        // Matches InAppWorkerHandler's own Instant.parse() on the receiving end.
        payload.put("createdAt", Instant.now().toString());

        // channel_* attributes - each SQS queue's own subscription (infra/terraform/sns.tf) has
        // a filter policy matching on exactly one of these, so setting/omitting an attribute
        // here is the whole routing decision, looked up per notification type above. Nothing
        // here ever sets channel_push regardless of type/mapping - with no device-token
        // registration feature built yet, recipientDeviceToken above is always null, so a push
        // message could never do anything but burn a Lambda invocation on an automatic no-op.
        Set<String> channels = CHANNELS_BY_TYPE.getOrDefault(event.getType(), DEFAULT_CHANNELS);
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        for (String channel : channels) {
            attributes.put(channel, stringAttribute("true"));
        }

        try {
            String json = MAPPER.writeValueAsString(payload);
            client().publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(json)
                    .messageAttributes(attributes)
                    .build());
        } catch (Exception e) {
            // Never let a notification failure break whatever real action triggered it (a
            // follow, a comment) - same "log it, don't propagate" tolerance the rest of this
            // project already applies to every optional/best-effort integration.
            log.error("Failed to publish notification event to SNS: {}", e.getMessage());
        }
    }

    private static MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
