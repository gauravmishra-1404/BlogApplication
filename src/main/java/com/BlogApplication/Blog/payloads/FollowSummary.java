package com.BlogApplication.Blog.payloads;

// Response shape for the follow-toggle endpoint.
public class FollowSummary {
    private boolean following;
    private long followerCount;

    public FollowSummary() {
    }

    public FollowSummary(boolean following, long followerCount) {
        this.following = following;
        this.followerCount = followerCount;
    }

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }
}
