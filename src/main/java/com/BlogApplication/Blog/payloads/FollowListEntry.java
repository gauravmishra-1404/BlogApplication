package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.User;

import java.time.LocalDateTime;

// One row in the followers/following list modal. "self" and "followingThisUser" are both
// relative to the person VIEWING the modal, not the profile it's opened from - the button (or
// "You" badge) each row shows always reflects the viewer's own relationship to that row's user.
public class FollowListEntry {
    private User user;
    private LocalDateTime followedAt;
    private boolean followingThisUser;
    private boolean self;

    public FollowListEntry(User user, LocalDateTime followedAt, boolean followingThisUser, boolean self) {
        this.user = user;
        this.followedAt = followedAt;
        this.followingThisUser = followingThisUser;
        this.self = self;
    }

    public User getUser() {
        return user;
    }

    public LocalDateTime getFollowedAt() {
        return followedAt;
    }

    public boolean isFollowingThisUser() {
        return followingThisUser;
    }

    public boolean isSelf() {
        return self;
    }
}
