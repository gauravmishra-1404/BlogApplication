package com.BlogApplication.Blog.exceptions;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String email) {
        super("An account with email " + email + " already exists");
    }
}
