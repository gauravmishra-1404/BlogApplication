package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.ShortDetail;
import com.BlogApplication.Blog.payloads.ShortDto;
import com.BlogApplication.Blog.payloads.ShortListing;

import java.security.Principal;

// Mirrors PostService's shape, scoped down for Shorts - no search/tags/authors, no repost counts.
public interface ShortService {

    void save(ShortDto shortDto, Principal principal);

    ShortDto getShortById(int id);

    void updateShortByID(ShortDto shortDto, int id);

    void isDeleted(int id);

    // Mirrors PostService.getPostDetail - call after PostViewService-equivalent
    // (ShortViewService.recordView) has already recorded the view.
    ShortDetail getShortDetail(int id, String userEmail);

    // Mirrors PostService.getListing, for the immersive Shorts feed - plain reverse-chronological,
    // no query/author/tag filters.
    ShortListing getShortsListing(int page, int size);

    // Same shape as getShortsListing's first page, but with one specific Short pinned first -
    // backs a real per-Short URL (GET /shorts/{id}), the entry-point half of how YouTube's own
    // /shorts/<id> works: it always lands on that one video first, then continues into a queue
    // (personalized, for YouTube - here, the same plain shared feed order everyone else sees,
    // since this app has no recommendation engine to diverge per viewer). Falls back to the
    // plain first page if startId doesn't exist/isn't visible - a stale or broken share link
    // should still land somewhere useful, not error.
    ShortListing getShortsListingStartingAt(int startId, int size);
}
