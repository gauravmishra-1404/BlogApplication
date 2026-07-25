package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ReactionType;

// Projection for CommentReactionRepo.countGroupedByCommentIds - one grouped query for a whole
// comment thread's like/dislike counts instead of a query per comment.
public interface CommentReactionCount {
    Integer getCommentId();

    ReactionType getReactionType();

    Long getCount();
}
