package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.models.VerificationToken;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.EmailService;
import com.BlogApplication.Blog.services.VerificationTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

// Handles email verification (post-registration) and forgot/reset password. Both flows share
// the same VerificationToken mechanism (a random, single-use, 30-minute-expiring token) and
// the same anti-enumeration pattern: whether or not an email is registered, the response the
// caller sees is identical, so this can't be used to probe which emails have accounts.
@Controller
public class AuthRecoveryController {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // --- Email verification ---

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam("token") String token, Model model) {
        Optional<VerificationToken> verificationToken = verificationTokenService.validate(token, TokenPurpose.VERIFY_EMAIL);
        if (verificationToken.isPresent()) {
            User user = verificationToken.get().getUser();
            user.setEmailVerified(true);
            userRepo.save(user);
            verificationTokenService.markUsed(verificationToken.get());
            return "redirect:/login?verified";
        }

        // Token isn't valid right now, but if it once belonged to an account that's already
        // verified, treat this as success rather than an error. Mail clients, antivirus link
        // scanners, and chat-app link previews commonly pre-fetch URLs found in emails before
        // the user ever clicks, which silently burns a single-use token on the first visit -
        // without this, the user's own (real) click would wrongly show "invalid or expired".
        Optional<VerificationToken> anyToken = verificationTokenService.find(token, TokenPurpose.VERIFY_EMAIL);
        if (anyToken.isPresent() && anyToken.get().getUser().isEmailVerified()) {
            return "redirect:/login?verified";
        }

        model.addAttribute("error", "That verification link is invalid or has expired.");
        return "verifyEmailResult";
    }

    @GetMapping("/resend-verification")
    public String resendVerificationForm() {
        return "resendVerification";
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam("email") String email, Model model) {
        userRepo.findByEmail(email.trim().toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    String token = verificationTokenService.createToken(user, TokenPurpose.VERIFY_EMAIL);
                    emailService.sendVerificationEmail(user, token);
                });

        model.addAttribute("message", "If that email has an unverified account, we've sent a new verification link.");
        return "resendVerification";
    }

    // --- Forgot / reset password ---

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgotPassword";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam("email") String email, Model model) {
        userRepo.findByEmail(email.trim().toLowerCase())
                .ifPresent(user -> {
                    String token = verificationTokenService.createToken(user, TokenPurpose.RESET_PASSWORD);
                    emailService.sendPasswordResetEmail(user, token);
                });

        model.addAttribute("message", "If that email has an account, we've sent a link to reset the password.");
        return "forgotPassword";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        if (verificationTokenService.validate(token, TokenPurpose.RESET_PASSWORD).isEmpty()) {
            model.addAttribute("error", "That reset link is invalid or has expired.");
            return "resetPassword";
        }
        model.addAttribute("token", token);
        return "resetPassword";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token,
                                 @RequestParam("password") String password,
                                 Model model) {
        Optional<VerificationToken> verificationToken = verificationTokenService.validate(token, TokenPurpose.RESET_PASSWORD);
        if (verificationToken.isEmpty()) {
            model.addAttribute("error", "That reset link is invalid or has expired.");
            return "resetPassword";
        }

        User user = verificationToken.get().getUser();
        user.setPassword(passwordEncoder.encode(password));
        userRepo.save(user);
        verificationTokenService.markUsed(verificationToken.get());

        return "redirect:/login?resetSuccess";
    }
}
