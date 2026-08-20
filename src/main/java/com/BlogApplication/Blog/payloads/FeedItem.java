package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// One row in a feed (the dashboard, the Following feed) - either a post's own publish, or a
// repost of it. Deliberately a wrapper around Post rather than changing what Post means: the
// SAME post can appear as more than one FeedItem in the same page (its own publish, plus one
// entry per person who reposted it, if more than one of those lands in the current window) -
// exactly how a real repost feature behaves, and the reason PostListing.posts couldn't stay a
// plain List<Post> once reposts needed to interleave into it.
@Getter
@Builder
@AllArgsConstructor
public class FeedItem {
    private Post post;

    // null for a normal (non-repost) entry - postRows.html's own repost-attribution tag only
    // renders when this is set.
    private User repostedBy;

    public static FeedItem of(Post post) {
        return FeedItem.builder().post(post).build();
    }

    public static FeedItem repostOf(Post post, User repostedBy) {
        return FeedItem.builder().post(post).repostedBy(repostedBy).build();
    }
}
