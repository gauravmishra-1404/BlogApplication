package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.BookmarkSummary;

import java.util.Optional;

public interface BookmarkService {
    // Empty if the post doesn't exist, is soft-deleted, or isn't published - drafts aren't
    // bookmarkable, there's nothing to read later for anyone but the author, who can already
    // reach it from Drafts.
    Optional<BookmarkSummary> toggle(String userEmail, int postId);
}
