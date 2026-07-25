package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ReactionType;

// Projection for PostReactionRepo.countGroupedByPostIds - one grouped query for a whole page of
// posts' like/dislike counts (e.g. the dashboard listing) instead of a query per post.
public interface PostReactionCount {
    Integer getPostId();

    ReactionType getReactionType();

    Long getCount();
}
