package com.bodhsea.notifications.push;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Consumes the push queue - one FCM send per message. FCM device tokens go stale constantly
// (app uninstalled, token rotated, etc.) - a bad-token failure is NOT retried (there's nothing
// a retry fixes), only genuine transient errors are, so this doesn't pointlessly dead-letter
// every notification aimed at someone who uninstalled the app months ago.
public class PushWorkerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SERVICE_ACCOUNT_JSON = System.getenv("FCM_SERVICE_ACCOUNT_JSON");

    // Firebase SDK initialization is expensive (parses/validates the credential, sets up an
    // HTTP transport) - done once per Lambda execution environment (static init, reused across
    // warm invocations of the same instance) rather than per-message, same reasoning any
    // connection-pool/client setup gets hoisted out of a hot path.
    private static volatile boolean initialized = false;

    private static synchronized void ensureInitialized() throws Exception {
        if (initialized || SERVICE_ACCOUNT_JSON == null || SERVICE_ACCOUNT_JSON.isBlank()) {
            return;
        }
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(
                            new ByteArrayInputStream(SERVICE_ACCOUNT_JSON.getBytes(StandardCharsets.UTF_8))))
                    .build();
            FirebaseApp.initializeApp(options);
        }
        initialized = true;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        try {
            ensureInitialized();
        } catch (Exception e) {
            context.getLogger().log("Firebase init failed: " + e.getMessage());
            // Every message in this batch fails together here - a broken/missing credential
            // isn't a per-message problem, retrying won't fix any of them individually either.
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
            return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
        }

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                send(message.getBody(), context);
            } catch (Exception e) {
                context.getLogger().log("Push send failed for message " + message.getMessageId() + ": " + e.getMessage());
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private void send(String rawBody, Context context) throws Exception {
        NotificationMessage notification = MAPPER.readValue(rawBody, NotificationMessage.class);

        if (SERVICE_ACCOUNT_JSON == null || SERVICE_ACCOUNT_JSON.isBlank()) {
            // Push isn't configured yet (no FCM service account set) - logged and treated as
            // handled, not a failure, same "missing optional config" tolerance the main app
            // already uses for Cloudinary. Don't dead-letter every push notification just
            // because this channel hasn't been set up yet.
            context.getLogger().log("Skipping push send - FCM_SERVICE_ACCOUNT_JSON not configured");
            return;
        }
        if (notification.getRecipientDeviceToken() == null || notification.getRecipientDeviceToken().isBlank()) {
            // No device registered for this user (never opened the app / notifications not
            // enabled) - not a failure, nothing to retry.
            return;
        }

        Message message = Message.builder()
                .setToken(notification.getRecipientDeviceToken())
                .setNotification(Notification.builder()
                        .setTitle(notification.getTitle() != null ? notification.getTitle() : "Bodh Sea")
                        .setBody(notification.getBody() != null ? notification.getBody() : "")
                        .build())
                .putData("targetUrl", notification.getTargetUrl() != null ? notification.getTargetUrl() : "/home")
                .build();

        FirebaseMessaging.getInstance().send(message);
    }
}
