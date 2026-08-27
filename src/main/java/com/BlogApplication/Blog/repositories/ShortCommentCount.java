package com.BlogApplication.Blog.repositories;

// Mirrors CommentCount exactly - lets ShortCommentRepo.countGroupedByShortIds fetch comment
// counts for a whole page of Shorts in one query instead of one COUNT per Short.
public interface ShortCommentCount {
    Integer getShortId();

    Long getCommentCount();
}
