package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.User;

// Projection for the "Follow" directory page - every user on the platform with their follower
// count, most-followed first. Left-joined from User (not Follow) so a user with zero followers
// still appears in the list, unlike AuthorPostCount's inner-join-shaped query which only makes
// sense for authors who actually have posts.
public interface UserFollowerCount {
    User getUser();

    Long getFollowerCount();
}
