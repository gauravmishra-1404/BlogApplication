package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.VisitorIdentityService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VisitorIdentityServiceImpl implements VisitorIdentityService {

    private static final String COOKIE_NAME = "visitor_id";
    private static final int COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365; // 1 year

    @Autowired
    private UserRepo userRepo;

    @Override
    public String resolveViewerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        boolean isLoggedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        if (isLoggedIn) {
            return userRepo.findByEmail(authentication.getName())
                    .map(user -> "u:" + user.getId())
                    .orElseGet(() -> resolveAnonymousToken(request, response));
        }
        return resolveAnonymousToken(request, response);
    }

    private String resolveAnonymousToken(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return "a:" + cookie.getValue();
                }
            }
        }

        String newId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(COOKIE_NAME, newId);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return "a:" + newId;
    }
}
