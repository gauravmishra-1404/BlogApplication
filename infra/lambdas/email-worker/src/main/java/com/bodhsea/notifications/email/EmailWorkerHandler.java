package com.bodhsea.notifications.email;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Consumes the email queue - one SendGrid call per message. Reports per-message failures back
// to SQS (ReportBatchItemFailures) rather than failing the whole batch on one bad message, so a
// single rejected send doesn't force 9 already-successful ones in the same batch to be retried
// and sent twice.
public class EmailWorkerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Explicit HTTP/1.1, same reasoning as the main app's own SendGridEmailService - avoids a
    // TLS/ALPN handshake issue seen against these mail-API providers under default HTTP/2
    // negotiation.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiKey = System.getenv("SENDGRID_API_KEY");
    private final String fromEmail = System.getenv("SENDGRID_FROM_EMAIL");

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                send(message.getBody());
            } catch (Exception e) {
                context.getLogger().log("Email send failed for message " + message.getMessageId() + ": " + e.getMessage());
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private void send(String rawBody) throws Exception {
        NotificationMessage notification = MAPPER.readValue(rawBody, NotificationMessage.class);

        if (apiKey == null || apiKey.isBlank()) {
            // Same tolerance the main app's own EmailService uses for a missing key - but here,
            // throwing (rather than silently logging-and-returning) is the right call: this
            // Lambda's whole job is delivery, so a missing key should count as a failed attempt
            // and go through the normal retry/DLQ path rather than being silently swallowed.
            throw new IllegalStateException("SENDGRID_API_KEY is not configured");
        }
        if (notification.getRecipientEmail() == null || notification.getRecipientEmail().isBlank()) {
            // Not retryable - no amount of retrying fixes a message with no destination
            // address. Logged and treated as "succeeded" (no BatchItemFailure) so it doesn't
            // loop forever into the DLQ for a data problem, not a delivery problem.
            return;
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", notification.getRecipientEmail())))),
                "from", Map.of("email", fromEmail, "name", "Bodh Sea"),
                "subject", notification.getTitle() != null ? notification.getTitle() : "New notification",
                "content", List.of(Map.of("type", "text/plain", "value", notification.getBody() != null ? notification.getBody() : ""))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SENDGRID_API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new RuntimeException("SendGrid returned " + response.statusCode() + ": " + response.body());
        }
    }
}
