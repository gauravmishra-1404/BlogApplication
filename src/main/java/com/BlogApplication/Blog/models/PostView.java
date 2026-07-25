package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// Records one unique viewer per post. This is a blog-exclusive table (not shared with the
// other app on this database, unlike users/tags/comments) - safe to evolve freely.
@Entity
@Table(name = "post_views", uniqueConstraints = @UniqueConstraint(name = "uk_post_view_visitor", columnNames = {"post_id", "viewer_token"}))
public class PostView {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // "u:<userId>" for a logged-in account, "a:<cookieUuid>" for an anonymous visitor tracked
    // via a long-lived cookie - see VisitorIdentityService. The unique constraint above on
    // (post_id, viewer_token) is what makes this a *unique* view count rather than a raw hit
    // counter: the same visitor reloading the page never counts twice.
    @Column(name = "viewer_token", nullable = false)
    private String viewerToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public String getViewerToken() {
        return viewerToken;
    }

    public void setViewerToken(String viewerToken) {
        this.viewerToken = viewerToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
