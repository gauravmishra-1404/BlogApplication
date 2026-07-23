package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VerificationTokenRepo extends JpaRepository<VerificationToken, Integer> {
    Optional<VerificationToken> findByTokenAndPurpose(String token, TokenPurpose purpose);
}
