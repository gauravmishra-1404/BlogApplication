package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.User;

public interface EmailService {
    void sendVerificationEmail(User user, String token);

    void sendPasswordResetEmail(User user, String token);
}
