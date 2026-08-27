package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Mirrors Comment.java's shape exactly, but on its own table (short_comments) with a direct FK to
// ShortVideo instead of Post - see this feature's plan for why Shorts get fully separate
// interaction tables rather than sharing Post's. Deliberately does NOT carry Comment's "name"
// field - that only exists because "comments" is shared with an unrelated Q&A app on this same
// database; short_comments is a brand-new table with no such constraint.
@Entity
@Table(name = "short_comments")
public class ShortComment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "edited")
    private Boolean edited;

    @Column(name = "deleted")
    private Boolean deleted;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    // Field can't be named "short" (reserved primitive keyword), hence shortVideo - matches the
    // entity type it points to.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "short_id")
    private ShortVideo shortVideo;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ShortComment parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER)
    @BatchSize(size = 20)
    private List<ShortComment> replies = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEdited() {
        return Boolean.TRUE.equals(edited);
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ShortVideo getShortVideo() {
        return shortVideo;
    }

    public void setShortVideo(ShortVideo shortVideo) {
        this.shortVideo = shortVideo;
    }

    public ShortComment getParent() {
        return parent;
    }

    public void setParent(ShortComment parent) {
        this.parent = parent;
    }

    public List<ShortComment> getReplies() {
        return replies;
    }

    public void setReplies(List<ShortComment> replies) {
        this.replies = replies;
    }

    // Mirrors Comment.getVisibleReplies() exactly - filters soft-deleted replies out for template
    // rendering.
    public List<ShortComment> getVisibleReplies() {
        return replies.stream().filter(reply -> !reply.isDeleted()).toList();
    }
}
