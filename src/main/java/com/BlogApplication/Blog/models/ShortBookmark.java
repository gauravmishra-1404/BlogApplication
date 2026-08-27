package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// Mirrors Bookmark.java exactly - pure existence row (created/deleted, never mutated) against its
// own table instead of sharing post_bookmarks. See this feature's plan.
@Entity
@Table(name = "short_bookmarks", uniqueConstraints = @UniqueConstraint(name = "uk_short_bookmark_user", columnNames = {"short_id", "user_id"}))
public class ShortBookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_id", nullable = false)
    private ShortVideo shortVideo;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
