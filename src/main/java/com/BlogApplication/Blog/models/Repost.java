package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// A repost is pure existence - created or deleted, never mutated in place, same shape as
// Bookmark and Follow. Standalone table (reposts), blog-exclusive - not shared with the other
// app on this database.
//
// Deliberately NOT a Post subtype or a Post with an "originalPostId" pointer - that's the
// mechanism a QUOTE repost (add your own comment, get your own independent stats) would need,
// not a plain repost. A plain repost never creates new content: it's a pointer saying "this
// user reposted this post at this time", and every view of it (feed, profile) renders straight
// from the referenced Post - same author, same content, same like/comment/view counts.
//
// Lombok annotations - same combo PostMedia.java (the first @Entity to use them) already
// establishes: @Getter/@Setter for the usual accessor pair, @NoArgsConstructor because
// Hibernate requires a public no-args constructor on every @Entity, @AllArgsConstructor for
// @Builder's generated .build() to call under the hood, @Builder for a fluent
// Repost.builder().post(p).user(u).createdAt(now).build() construction style.
@Entity
@Table(name = "reposts", uniqueConstraints = @UniqueConstraint(name = "uk_repost_user_post", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repost {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
