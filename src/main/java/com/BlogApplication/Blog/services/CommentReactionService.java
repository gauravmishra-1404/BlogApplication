package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.payloads.ReactionSummary;

import java.util.List;
import java.util.Map;

public interface CommentReactionService {
    ReactionSummary toggle(int commentId, String userEmail, ReactionType type);

    ReactionSummary getSummary(int commentId, String userEmail);

    // One summary per id, batched - for rendering a whole comment thread without a query per
    // comment. Ids with no reactions at all still get an entry (zero/zero/null), not omitted.
    Map<Integer, ReactionSummary> getSummaries(List<Integer> commentIds, String userEmail);
}
