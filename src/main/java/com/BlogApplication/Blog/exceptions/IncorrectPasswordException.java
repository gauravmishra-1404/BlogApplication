package com.BlogApplication.Blog.exceptions;

// Thrown when a sensitive personal-info change (email, mobile number) is requested with the
// wrong current password - these fields double as account-recovery credentials, so changing
// them is gated behind re-entering the password, same reasoning X gates its own Account
// Information screen behind a password re-check.
public class IncorrectPasswordException extends RuntimeException {
    public IncorrectPasswordException(String message) {
        super(message);
    }
}
