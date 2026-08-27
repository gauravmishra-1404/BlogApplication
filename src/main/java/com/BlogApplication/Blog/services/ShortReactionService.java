package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.payloads.ReactionSummary;

import java.util.List;
import java.util.Map;

// Mirrors PostReactionService exactly, against Shorts instead of Posts.
public interface ShortReactionService {
    ReactionSummary toggle(int shortId, String userEmail, ReactionType type);

    ReactionSummary getSummary(int shortId, String userEmail);

    Map<Integer, ReactionSummary> getSummaries(List<Integer> shortIds, String userEmail);
}
