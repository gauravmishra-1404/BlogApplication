package com.BlogApplication.Blog.payloads;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

// Everything the dashboard (full page load) and its infinite-scroll fragment endpoint need to
// render one batch of posts - assembled by PostService.getListing() so the controller only
// calls one service method and copies its fields onto the Model, instead of orchestrating
// PostService/PostViewService/PostReactionService itself and computing hasNextPage by hand.
//
// posts is List<FeedItem>, not List<Post> - a repost interleaves into this same list as its own
// entry (see FeedItem's own comment for why a plain Post list can't represent that).
// repostCounts/repostedPostIds mirror commentCounts/bookmarkedPostIds's own shape: one grouped
// query per page, not one per post.
@Value
@Builder
public class PostListing {
    List<FeedItem> posts;
    Map<Integer, Long> viewCounts;
    Map<Integer, ReactionSummary> reactions;
    Map<Integer, Long> commentCounts;
    Map<Integer, Long> repostCounts;
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
