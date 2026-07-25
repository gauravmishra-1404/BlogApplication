package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.payloads.ReactionSummary;

import java.util.List;
import java.util.Map;

public interface PostReactionService {
    // Clicking the same reaction again removes it; clicking the other one switches it.
    ReactionSummary toggle(int postId, String userEmail, ReactionType type);

    // userEmail may be null/anonymous - counts are public, userReaction is just null in that case.
    ReactionSummary getSummary(int postId, String userEmail);

    // One summary per id, batched - for a listing (e.g. the dashboard) without a query per post.
    Map<Integer, ReactionSummary> getSummaries(List<Integer> postIds, String userEmail);
}
