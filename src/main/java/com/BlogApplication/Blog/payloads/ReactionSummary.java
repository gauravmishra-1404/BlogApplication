package com.BlogApplication.Blog.payloads;

// Response shape for both the initial page render and the AJAX toggle endpoints - userReaction
// is null for a logged-out viewer or a user who hasn't reacted, "LIKE"/"DISLIKE" otherwise.
public class ReactionSummary {
    private long likeCount;
    private long dislikeCount;
    private String userReaction;

    public ReactionSummary() {
    }

    public ReactionSummary(long likeCount, long dislikeCount, String userReaction) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.userReaction = userReaction;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public String getUserReaction() {
        return userReaction;
    }

    public void setUserReaction(String userReaction) {
        this.userReaction = userReaction;
    }
}
