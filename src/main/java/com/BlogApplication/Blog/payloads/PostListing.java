package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.Post;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

// Everything the dashboard (full page load) and its infinite-scroll fragment endpoint need to
// render one batch of posts - assembled by PostService.getListing() so the controller only
// calls one service method and copies its fields onto the Model, instead of orchestrating
// PostService/PostViewService/PostReactionService itself and computing hasNextPage by hand.
@Value
@Builder
public class PostListing {
    List<Post> posts;
    Map<Integer, Long> viewCounts;
    Map<Integer, ReactionSummary> reactions;
    Map<Integer, Long> commentCounts;
    int currentPage;
    int totalPages;
    long totalItems;
    int pageSize;
    boolean hasNextPage;
    String activeQuery;
    List<String> activeAuthors;
    List<String> activeTags;
    String activeOrder;
}
