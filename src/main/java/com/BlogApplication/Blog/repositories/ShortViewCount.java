package com.BlogApplication.Blog.repositories;

// Mirrors PostViewCount exactly - lets ShortViewRepo.countGroupedByShortIds fetch view counts for
// a whole page of Shorts in one query instead of one COUNT per Short.
public interface ShortViewCount {
    Integer getShortId();

    Long getViewCount();
}
