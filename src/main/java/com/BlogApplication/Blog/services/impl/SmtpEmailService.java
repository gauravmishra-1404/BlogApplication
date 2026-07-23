package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

// Real outbound mail via SMTP (Gmail). Active whenever app.mail.enabled is true or unset -
// i.e. in production. Local/dev and test profiles set app.mail.enabled=false so they get
// ConsoleEmailService instead, since they have no SMTP credentials configured.
@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void sendVerificationEmail(User user, String token) {
        String link = baseUrl + "/verify-email?token=" + token;
        // user.getName()/getEmail() are user-supplied at registration - escape before they
        // land in the HTML body so a name like "<script>..." can't inject markup into the email.
        String safeName = HtmlUtils.htmlEscape(user.getName());
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());

        String html = EmailTemplates.render(
                "Verify your email",
                "Welcome aboard, " + safeName,
                "One click and your account is ready to go. This confirms <strong>" + safeEmail
                        + "</strong> belongs to you before you start publishing.",
                "Verify email address",
                link,
                "This link expires in 30 minutes. If you didn't create this account, you can ignore this email."
        );
        String plainText = "Hi " + user.getName() + ",\n\n"
                + "Click the link below to verify your email and activate your account:\n"
                + link + "\n\n"
                + "This link expires in 30 minutes. If you didn't create this account, ignore this email.";

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
                "This link expires in 30 minutes. If you didn't request this, you can safely ignore this email - your password won't change."
        );
        String plainText = "Hi " + user.getName() + ",\n\n"
                + "Click the link below to choose a new password:\n"
                + link + "\n\n"
                + "This link expires in 30 minutes. If you didn't request this, you can ignore this email.";

        send(user.getEmail(), "Reset your Bodh Sea password", plainText, html);
    }

    // Best-effort delivery: a broken/unconfigured SMTP account shouldn't turn registration or
    // password reset into a 500 error for the user - the caller already shows a generic
    // "check your email" message regardless of whether sending actually succeeded, and the
    // resend-verification flow lets them retry once credentials are fixed.
    //
    // Sent as multipart/alternative (plain text + HTML) rather than HTML-only: better spam-score
    // behavior, and a readable fallback for any client that can't render the HTML part.
    //
    // The logo is an attached inline (CID) image, not inline <svg> - Gmail's webmail sanitizer
    // strips <svg> from email bodies entirely, which is why an earlier version of this email
    // showed blank space where the mark should be. CID images are treated as part of the
    // message itself (not remote-fetched), so clients render them without a "show images" prompt.
    private void send(String to, String subject, String plainText, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (!fromAddress.isBlank()) {
                helper.setFrom(fromAddress, "Bodh Sea");
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            helper.addInline("brandMark", new ClassPathResource("email/brand-mark.png"));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}
