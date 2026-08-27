package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Mirrors PostDto, scoped down for a Short: no title/tags/mediaJson/mediaList - just a caption and
// a single videoUrl (a Short is always exactly one video, never a gallery). New class, so Lombok
// covers the boilerplate (@Getter/@Setter/@Builder) same as PostMedia.java's own precedent -
// unlike PostDto, there's no legacy hand-written style here to stay consistent with.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortDto {
    private int id;
    private String caption;
    private String videoUrl;
    // Phase 2 transcoding pipeline output - all null until the transcode-worker Lambda processes
    // this Short (or forever, if AWS media is disabled - e.g. local dev). See ShortVideo.java's
    // own comment on the same three fields.
    private String transcodedVideoUrl;
    private String thumbnailUrl;
    private String processingStatus;
    private LocalDateTime publishedAt;
    private boolean published;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @JsonIgnore
    private User user;
    private long viewCount;
}
