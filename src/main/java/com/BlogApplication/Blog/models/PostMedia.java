package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// One row per image/video attached to a post, in display order. Unlike Post.tagList (shared
// Tags rows, reused across posts, deliberately NOT cascaded - see Post.java's own comment on
// the bug that caused), a PostMedia row has no meaning outside the one post it belongs to, so
// this relationship IS fully cascaded (see Post.media's own @OneToMany) - a different
// relationship shape for a genuinely different kind of "belongs to."
//
// Lombok annotations (this is the first @Entity in the project to use them - every other one
// still has hand-written getters/setters, kept that way rather than mass-converted):
//   @Getter/@Setter - generates the usual getX()/setX() pair for every field, at compile time -
//     same methods you'd type by hand, just not physically present in this file.
//   @NoArgsConstructor - a public no-args constructor. JPA/Hibernate REQUIRES this on every
//     @Entity (it builds instances via reflection, not your code's constructors), so this one
//     isn't optional even with @Builder present.
//   @AllArgsConstructor - a constructor taking every field, in declaration order. This is what
//     @Builder's generated .build() call actually invokes under the hood.
//   @Builder - adds a fluent PostMedia.builder().mediaUrl(...).mediaType(...).build() style
//     construction API, instead of `new PostMedia(); setX(); setY();` - reads better when
//     building a row with several fields at once (see PostServiceImpl's media-resolution code).
@Entity
@Table(name = "post_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMedia {

    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // CloudFront URL (https://<distribution>/posts/...), never a raw S3 URL - the bucket itself
    // is private, only reachable through the CDN (see infra/terraform/media.tf).
    @Column(name = "media_url", nullable = false, length = 500)
    private String mediaUrl;

    @Column(name = "media_type", nullable = false, length = 10)
    private String mediaType;

    // Display order within the post's gallery - 0-based, set from the order the files were
    // selected/uploaded client-side.
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean isVideo() {
        return VIDEO.equals(mediaType);
    }
}
