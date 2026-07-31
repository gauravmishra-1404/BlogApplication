package com.BlogApplication.Blog.payloads;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

// Everything the full post page (viewPostByID.html) and the dashboard's modal post view
// (fragments/postModal.html) need beyond the raw PostDto - assembled by
// PostService.getPostDetail() so both callers share one method instead of each independently
// composing PostReactionService/CommentReactionService themselves.
@Value
@Builder
public class PostDetail {
    PostDto post;
    ReactionSummary postReaction;
    Map<Integer, ReactionSummary> commentReactions;
}
