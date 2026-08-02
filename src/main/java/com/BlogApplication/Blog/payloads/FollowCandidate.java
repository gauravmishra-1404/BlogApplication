package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.User;
import lombok.Builder;
import lombok.Value;

// One row on the "Follow" directory page - every user on the platform, ranked by follower
// count. followingThisUser/self are both relative to the VIEWER, matching the same convention
// FollowListEntry already uses for the followers/following modal.
@Value
@Builder
public class FollowCandidate {
    User user;
    long followerCount;
    boolean followingThisUser;
    boolean self;
}
