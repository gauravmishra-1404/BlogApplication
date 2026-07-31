package com.BlogApplication.Blog.repositories;

// Projection for a single grouped-count row - lets CommentRepo.countGroupedByPostIds fetch
// comment counts for a whole page of posts in one query instead of one COUNT per post, same
// reasoning as PostViewCount.
public interface CommentCount {
    Integer getPostId();

    Long getCommentCount();
}
