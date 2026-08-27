package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.BookmarkSummary;

import java.util.Optional;

// Mirrors BookmarkService exactly, against Shorts instead of Posts.
public interface ShortBookmarkService {
    Optional<BookmarkSummary> toggle(String userEmail, int shortId);
}
