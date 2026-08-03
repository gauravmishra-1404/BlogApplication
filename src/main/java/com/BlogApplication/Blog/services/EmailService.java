package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.User;

public interface EmailService {
    void sendVerificationEmail(User user, String token);

    void sendPasswordResetEmail(User user, String token);

    // newEmail is passed explicitly rather than read off `user` - the confirmation link must go
    // to the NEW address being confirmed, while user.getEmail() is still the current (old) one
    // until that link is actually clicked.
    void sendEmailChangeConfirmation(User user, String newEmail, String token);
}
