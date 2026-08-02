package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// A follow is pure existence - created or deleted, never mutated in place (unlike PostReaction,
// which flips LIKE/DISLIKE on the same row) - so there's no "type"/updatedAt column, just the
// pair and when it started. Standalone table (user_follows, not the more generic "follows") -
// blog-exclusive, not shared with the other app on this database, named to avoid colliding with
// anything that app might already have or add under a more generic name.
@Entity
@Table(name = "user_follows", uniqueConstraints = @UniqueConstraint(name = "uk_user_follow", columnNames = {"follower_id", "followed_id"}))
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_id", nullable = false)
    private User followed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public User getFollower() {
        return follower;
    }

    public void setFollower(User follower) {
        this.follower = follower;
    }

    public User getFollowed() {
        return followed;
    }

    public void setFollowed(User followed) {
        this.followed = followed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
