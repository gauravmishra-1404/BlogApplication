package com.BlogApplication.Blog.services;

import java.util.List;
import java.util.Map;

// Mirrors PostViewService exactly, against Shorts instead of Posts.
public interface ShortViewService {
    void recordView(int shortId, String viewerToken);

    long countViews(int shortId);

    Map<Integer, Long> countViewsForShorts(List<Integer> shortIds);
}
