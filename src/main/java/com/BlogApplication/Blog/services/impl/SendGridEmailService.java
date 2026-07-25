package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

// Real outbound mail via SendGrid's HTTP API (v3 Mail Send), called directly with Spring's
// RestClient rather than the sendgrid-java SDK - keeps the dependency footprint down and
// mirrors the same call shape already proven working against Resend. Active whenever
// app.mail.enabled is true or unset - i.e. in production. Local/dev and test profiles set
// app.mail.enabled=false so they get ConsoleEmailService instead, since they have no API key.
//
// This app has no owned domain, so it uses SendGrid's Single Sender Verification (one email
// address, verified by clicking a confirmation link - no DNS records needed) rather than full
// domain authentication. That's the piece Resend didn't offer, which is why this replaced it.
@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendGridEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailService.class);
    private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";

    // Explicit HTTP/1.1 avoided a TLS/ALPN handshake failure against Resend's API with the JDK
    // HttpClient's default HTTP/2 negotiation; kept here defensively since it costs nothing and
    // may avoid the same class of issue against SendGrid's endpoint.
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
            .build();

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${sendgrid.api-key:}")
    private String apiKey;

    @Value("${sendgrid.from-email:}")
    private String fromEmail;

    @Override
    public void sendVerificationEmail(User user, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        String safeName = HtmlUtils.htmlEscape(user.getName());
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());

        String html = EmailTemplates.render(
                "Verify your email",
                "Welcome aboard, " + safeName,
                "One click and your account is ready to go. This confirms <strong>" + safeEmail
                        + "</strong> belongs to you before you start publishing.",
                "Verify email address",
                link,
                "This link expires in 5 minutes. If you didn't create this account, you can ignore this email.",
                baseUrl + "/images/brand-mark.png"
        );
        String plainText = "Hi " + user.getName() + ",\n\n"
                + "Click the link below to verify your email and activate your account:\n"
                + link + "\n\n"
                + "This link expires in 5 minutes. If you didn't create this account, ignore this email.";

        send(user.getEmail(), "Confirm your Bodh Sea account", plainText, html);
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());

        String html = EmailTemplates.render(
                "Reset your password",
                "Let's get you back in",
                "We received a request to reset the password for <strong>" + safeEmail
                        + "</strong>. Choose a new one below.",
                "Choose new password",
                link,
                "This link expires in 5 minutes. If you didn't request this, you can safely ignore this email - your password won't change.",
                baseUrl + "/images/brand-mark.png"
        );
        String plainText = "Hi " + user.getName() + ",\n\n"
                + "Click the link below to choose a new password:\n"
                + link + "\n\n"
                + "This link expires in 5 minutes. If you didn't request this, you can ignore this email.";

        send(user.getEmail(), "Reset your Bodh Sea password", plainText, html);
    }

    // Best-effort delivery: a missing/invalid API key shouldn't turn registration or password
    // reset into a 500 error for the user - the caller already shows a generic "check your
    // email" message regardless of whether sending actually succeeded, and the
    // resend-verification flow lets them retry once the key is fixed.
    private void send(String to, String subject, String plainText, String html) {
        if (apiKey.isBlank()) {
            log.error("Failed to send email to {}: SENDGRID_API_KEY is not configured", to);
            return;
        }
        if (fromEmail.isBlank()) {
            log.error("Failed to send email to {}: SENDGRID_FROM_EMAIL is not configured", to);
            return;
        }
        try {
            restClient.post()
                    .uri(SENDGRID_API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                            "from", Map.of("email", fromEmail, "name", "Bodh Sea"),
                            "subject", subject,
                            "content", List.of(
                                    Map.of("type", "text/plain", "value", plainText),
                                    Map.of("type", "text/html", "value", html)
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.error("Failed to send email to {}: SendGrid returned {} - {}", to, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
