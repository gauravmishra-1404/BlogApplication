package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.services.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Dev/test stand-in for SmtpEmailService: no SMTP credentials required, just logs the link
// that would have been emailed so the verify/reset flows can be exercised locally.
@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false")
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public void sendVerificationEmail(User user, String token) {
        log.info("[DEV MAIL STUB] Verification link for {}: {}/verify-email?token={}", user.getEmail(), baseUrl, token);
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        log.info("[DEV MAIL STUB] Password reset link for {}: {}/reset-password?token={}", user.getEmail(), baseUrl, token);
    }
}
