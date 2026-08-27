package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// Mirrors PostView.java exactly - records one unique viewer per Short against its own table
// instead of sharing post_views. Reuses VisitorIdentityService unchanged (already generic, not
// Post-specific) for the viewerToken value. See this feature's plan.
@Entity
@Table(name = "short_views", uniqueConstraints = @UniqueConstraint(name = "uk_short_view_visitor", columnNames = {"short_id", "viewer_token"}))
public class ShortView {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_id", nullable = false)
    private ShortVideo shortVideo;

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

    public ShortVideo getShortVideo() {
        return shortVideo;
    }

    public void setShortVideo(ShortVideo shortVideo) {
        this.shortVideo = shortVideo;
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
