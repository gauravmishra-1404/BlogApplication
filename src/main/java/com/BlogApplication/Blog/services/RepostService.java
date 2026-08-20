package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.RepostSummary;

import java.util.Optional;

public interface RepostService {
    // Empty if the post doesn't exist, is soft-deleted, or isn't published - same "nothing to
    // act on" rule BookmarkService.toggle already applies; a draft has no repost button to click
    // in the first place (see PostController's rendering paths), this is the server-side backstop.
    Optional<RepostSummary> toggle(String userEmail, int postId);
}
