package com.BlogApplication.Blog.repositories;

// Projection for RepostRepo.countGroupedByPostIds - one grouped query for a whole page of posts'
// repost counts (e.g. the dashboard listing), same "one query per page, not per post" reasoning
// PostReactionCount already establishes for likes/dislikes.
public interface RepostCount {
    Integer getPostId();

    Long getCount();
}
