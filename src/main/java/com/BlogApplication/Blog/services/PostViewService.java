package com.BlogApplication.Blog.services;

import java.util.List;
import java.util.Map;

public interface PostViewService {
    void recordView(int postId, String viewerToken);

    long countViews(int postId);

    // Batched lookup for a page of posts (e.g. the dashboard listing) - one query instead of
    // one per post. Ids with zero views are simply absent from the map.
    Map<Integer, Long> countViewsForPosts(List<Integer> postIds);
}
