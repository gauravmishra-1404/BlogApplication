package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.models.VerificationToken;

import java.util.Optional;

public interface VerificationTokenService {
    String createToken(User user, TokenPurpose purpose);

    Optional<VerificationToken> validate(String token, TokenPurpose purpose);

    // Unfiltered lookup (ignores used/expired) - lets a caller tell "this token never
    // existed" apart from "this token was already consumed", which matters for making
    // repeat visits to a verification link idempotent instead of a hard error.
    Optional<VerificationToken> find(String token, TokenPurpose purpose);

    void markUsed(VerificationToken verificationToken);
}
