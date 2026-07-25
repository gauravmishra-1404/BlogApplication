package com.BlogApplication.Blog.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

// Resolves a stable identity for "who is looking at this" - logged-in users are identified by
// account, anonymous visitors by a long-lived tracking cookie. Built for post view counting, but
// deliberately generic: likes/dislikes and share tracking need the exact same "who is this,
// logged in or not" resolution, so they can reuse this instead of duplicating cookie handling.
public interface VisitorIdentityService {
    String resolveViewerToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response);
}
