package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "excerpt")
    private String excerpt;

    @Column(length = 10000, name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Nullable Boolean, not a primitive - existing rows never had this explicitly set (the
    // column existed but nothing ever called setPublished(true)), so a primitive would have
    // silently read every one of them as false via JDBC's getBoolean()-on-NULL behavior. Same
    // null-safe-default pattern as Post.deleted/Comment.edited/User.emailVerified: null reads as
    // published, since every post that exists today genuinely is (no draft feature yet - see
    // docs/feature_future.md). PostServiceImpl.save() sets this true explicitly on every new
    // publish; a future draft feature would set it false there instead until the user publishes.
    @Column(name = "is_published")
    private Boolean isPublished;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Soft delete: nullable Boolean so rows predating this feature default to "not deleted"
    // when null, same pattern as Comment.edited / User.emailVerified. "Deleting" a post just
    // hides it (excluded from listing/search/direct view) rather than removing the row -
    // avoids ever touching the comments/tags FK graph on delete.
    @Column(name = "deleted")
    private Boolean deleted;

    // No cascade: tags are looked up/created manually in PostServiceImpl.resolveTags(), and
    // CascadeType.ALL here (specifically REMOVE) was the cause of a serious bug - deleting a
    // post cascaded a remove onto its tags, and since Tags.postList cascades ALL right back,
    // it reached into every *other* post sharing that tag too, corrupting unrelated posts.
    @ManyToMany(fetch = FetchType.EAGER)
    List<Tags> tagList;

    @OneToMany(mappedBy = "post", fetch = FetchType.EAGER)
    private List<Comment> comments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isPublished() {
        return isPublished == null || isPublished;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public List<Tags> getTagList() {
        return tagList;
    }

    public void setTagList(List<Tags> tagList) {
        this.tagList = tagList;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean getPublished() {
        return isPublished();
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
