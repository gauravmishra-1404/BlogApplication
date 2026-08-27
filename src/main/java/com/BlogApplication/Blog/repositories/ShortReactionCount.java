package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ReactionType;

// Mirrors PostReactionCount exactly - one grouped query for a whole page of Shorts' like/dislike
// counts instead of a query per Short.
public interface ShortReactionCount {
    Integer getShortId();

    ReactionType getReactionType();

    Long getCount();
}
