package com.BlogApplication.Blog.repositories;

// Projection for a single grouped-count row - lets PostViewRepo.countGroupedByPostIds fetch view
// counts for a whole page of posts in one query instead of one COUNT per post.
public interface PostViewCount {
    Integer getPostId();

    Long getViewCount();
}
