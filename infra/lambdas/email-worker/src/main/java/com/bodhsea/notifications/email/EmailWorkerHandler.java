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
    // Used to turn targetUrl (a relative path, e.g. "/profile/priyasharma" - the same value the
    // in-app bell's own redirect uses directly) into an absolute link a mail client can follow.
    // Falls back to the real deployed URL if this env var is ever left unset, rather than
    // producing a broken relative link in a sent email.
    private final String appBaseUrl = envOrDefault("APP_BASE_URL", "https://blogapplication-2ncl.onrender.com");

    // Per-type copy - eyebrow label, CTA button text, and a body-sentence template (the
    // shell's own BODY slot, distinct from the subject/headline). Mirrors
    // SnsNotificationPublisher's own CHANNELS_BY_TYPE pattern on the producer side: one place
    // to add a new notification type's copy, rather than scattering per-type logic through
    // this method. Unmapped types fall back to a generic rendering below rather than failing.
    private static final Map<String, String> EYEBROW_BY_TYPE = Map.of("NEW_FOLLOWER", "NEW FOLLOWER");
    private static final Map<String, String> CTA_TEXT_BY_TYPE = Map.of("NEW_FOLLOWER", "View profile");
    private static final Map<String, String> BODY_TEMPLATE_BY_TYPE = Map.of(
            "NEW_FOLLOWER", "{actor} just followed you on Bodh Sea. Take a look at their profile, and follow back if you like what they write."
    );

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

        String type = notification.getType() != null ? notification.getType() : "";
        String headline = notification.getTitle() != null && !notification.getTitle().isBlank()
                ? notification.getTitle()
                : "New notification";
        String actorName = notification.getActorName() != null ? notification.getActorName() : "Someone";

        // Body-sentence for the shell's own BODY slot (distinct from the headline/subject) -
        // per-type template first (fills in {actor}), falling back to notification.getBody()
        // if a type has no template of its own, and finally to the headline itself so this can
        // never send an empty body (SendGrid rejects that outright - "must be a string at least
        // one character in length", the original bug this fallback chain already fixed once).
        String bodyPlain = BODY_TEMPLATE_BY_TYPE.containsKey(type)
                ? BODY_TEMPLATE_BY_TYPE.get(type).replace("{actor}", actorName)
                : notification.getBody() != null && !notification.getBody().isBlank()
                        ? notification.getBody()
                        : headline;

        String ctaUrl = notification.getTargetUrl() != null && !notification.getTargetUrl().isBlank()
                ? appBaseUrl + notification.getTargetUrl()
                : appBaseUrl;
        String eyebrow = EYEBROW_BY_TYPE.getOrDefault(type, "NOTIFICATION");
        String ctaText = CTA_TEXT_BY_TYPE.getOrDefault(type, "View on Bodh Sea");
        String note = "You're receiving this because of activity on your Bodh Sea account.";

        // {actor} in the body template is the only piece of untrusted (user-chosen display
        // name) content reaching the HTML version - escaped before it goes into a <p> tag, same
        // XSS-prevention reasoning the main app's own SendGridEmailService already applies to
        // every user-supplied value it interpolates into an email.
        String bodyHtml = BODY_TEMPLATE_BY_TYPE.containsKey(type)
                ? BODY_TEMPLATE_BY_TYPE.get(type).replace("{actor}", "<strong>" + escapeHtml(actorName) + "</strong>")
                : escapeHtml(bodyPlain);

        String html = EmailTemplates.render(eyebrow, escapeHtml(headline), bodyHtml, ctaText, ctaUrl, note,
                appBaseUrl + "/images/brand-mark.png");

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", notification.getRecipientEmail())))),
                "from", Map.of("email", fromEmail, "name", "Bodh Sea"),
                "subject", headline,
                "content", List.of(
                        Map.of("type", "text/plain", "value", bodyPlain + "\n\n" + ctaUrl),
                        Map.of("type", "text/html", "value", html)
                )
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

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : fallback;
    }

    // Minimal HTML escaping, hand-rolled rather than pulling in a library (Spring's HtmlUtils,
    // Apache Commons Text, etc.) for one method - this module is deliberately kept dependency-
    // light for Lambda cold-start reasons, same reasoning NotificationMessage.java's own doc
    // comment gives for not sharing a library across the 3 worker modules. Covers the 5
    // characters that matter for breaking out of an HTML attribute/text context.
    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
