package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.models.VerificationToken;
import com.BlogApplication.Blog.repositories.VerificationTokenRepo;
import com.BlogApplication.Blog.services.VerificationTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationTokenServiceImpl implements VerificationTokenService {

    private static final long VALIDITY_MINUTES = 5;

    @Autowired
    private VerificationTokenRepo verificationTokenRepo;

    @Override
    public String createToken(User user, TokenPurpose purpose) {
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setUser(user);
        verificationToken.setPurpose(purpose);
        verificationToken.setExpiryDate(LocalDateTime.now().plusMinutes(VALIDITY_MINUTES));
        verificationTokenRepo.save(verificationToken);
        return verificationToken.getToken();
    }

    @Override
    public Optional<VerificationToken> validate(String token, TokenPurpose purpose) {
        return verificationTokenRepo.findByTokenAndPurpose(token, purpose)
                .filter(t -> !t.isUsed())
                .filter(t -> !t.isExpired());
    }

    @Override
    public Optional<VerificationToken> find(String token, TokenPurpose purpose) {
        return verificationTokenRepo.findByTokenAndPurpose(token, purpose);
    }

    @Override
    public void markUsed(VerificationToken verificationToken) {
        verificationToken.setUsed(true);
        verificationTokenRepo.save(verificationToken);
    }
}
