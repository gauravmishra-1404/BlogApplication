package com.BlogApplication.Blog.payloads;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

// Mirrors PostListing, scoped down - no repost counts at all (Shorts are never repostable), and
// posts is a plain List<ShortDto> rather than PostListing's List<FeedItem>, since there's no
// repost-interleaving concept here. Assembled by ShortService.getShortsListing() so
// ShortsController only calls one service method, same shape as PostController does with
// PostListing.
@Value
@Builder
public class ShortListing {
    List<ShortDto> shorts;
    Map<Integer, Long> viewCounts;
    Map<Integer, ReactionSummary> reactions;
    Map<Integer, Long> commentCounts;
    int currentPage;
    int totalPages;
    long totalItems;
    int pageSize;
    boolean hasNextPage;
}
