package com.BlogApplication.Blog.repositories;

// Projection for the dashboard's "Trending tags" widget - one row per tag with how many
// visible (non-deleted) posts carry it, ordered by that count. Mirrors PostViewCount's shape.
public interface TagPostCount {
    String getName();

    Long getPostCount();
}
