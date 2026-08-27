package com.BlogApplication.Blog.payloads;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

// Mirrors PostDetail exactly - everything the Shorts feed/detail view needs beyond the raw
// ShortDto, assembled by ShortService.getShortDetail(). Reuses ReactionSummary as-is (already
// generic, no Post-specific fields).
@Value
@Builder
public class ShortDetail {
    ShortDto shortVideo;
    ReactionSummary reaction;
    Map<Integer, ReactionSummary> commentReactions;
}
