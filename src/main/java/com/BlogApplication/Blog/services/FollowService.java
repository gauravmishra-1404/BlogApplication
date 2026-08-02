package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.FollowSummary;

public interface FollowService {
    FollowSummary toggle(String followerEmail, String followedUsername);
}
