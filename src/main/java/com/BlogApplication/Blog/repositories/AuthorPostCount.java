package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.User;

// Projection for the dashboard's "Active writers" widget - one row per author with how many
// visible (non-deleted) posts they have, ordered by that count. Returns the real User (not just
// the plain author-name string PostRepo.distinctAuthor() gives) so the widget can render an
// actual avatar/profile link, not just a name.
public interface AuthorPostCount {
    User getUser();

    Long getPostCount();
}
