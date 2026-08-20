package com.BlogApplication.Blog.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Response shape for the repost-toggle endpoint. Unlike BookmarkSummary (a private, count-free
// state), repost count is public - shown on every card - so the toggle response carries the
// fresh total straight back, same "server is the source of truth after a write" reasoning
// PostReactionServiceImpl's own summary already follows for like/dislike counts.
//   Same Lombok combo PresignedUpload.java already uses for a plain response payload -
//   @Getter for the two accessors, @Builder for fluent construction, @AllArgsConstructor/
//   @NoArgsConstructor so @Builder has a constructor to call and Jackson can (de)serialize this.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RepostSummary {
    private boolean reposted;
    private long repostCount;
}
