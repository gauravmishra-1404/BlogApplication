package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // Only ever meaningful while isPublished is false - a scheduled post is exactly that: not
    // published yet, with a future time to become so. No separate status is introduced for this;
    // ScheduledPostPublisher's poller is the only thing that ever reads it, flipping isPublished
    // true (the same one-way-door PostServiceImpl.save()/updatePostByID() already implement) once
    // it's due. Bare LocalDateTime, no timezone handling - same as publishedAt/createdAt already.
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // @BatchSize (Hibernate, not JPA-standard) - without it, an EAGER collection gets loaded
    // with ONE SEPARATE QUERY PER PARENT ROW: rendering a 15-post feed page fires 15 queries
    // just for tagList, another 15 for comments, another 15 for media - 45+ extra round-trips
    // for one page, and it gets worse as the amount of content grows (exactly the "gets slower
    // over time" symptom this was added to fix). @BatchSize(size = 20) tells Hibernate to
    // instead batch up to 20 parent ids into ONE query per collection type
    // ("...WHERE post_id IN (?,?,?,...)") - same EAGER data, same everywhere it's already used,
    // just fetched in a handful of queries instead of dozens. Doesn't fix the N+1 shape itself
    // (still separate per collection type), but the size of the win here is large for a
    // one-line, zero-behavior-change annotation - the properly-fixed version (fetch-joined or
    // paginated projections tailored per query) is a bigger, riskier change for another day.
    //
    // No cascade: tags are looked up/created manually in PostServiceImpl.resolveTags(), and
    // CascadeType.ALL here (specifically REMOVE) was the cause of a serious bug - deleting a
    // post cascaded a remove onto its tags, and since Tags.postList cascades ALL right back,
    // it reached into every *other* post sharing that tag too, corrupting unrelated posts.
    @ManyToMany(fetch = FetchType.EAGER)
    @BatchSize(size = 20)
    List<Tags> tagList;

    @OneToMany(mappedBy = "post", fetch = FetchType.EAGER)
    @BatchSize(size = 20)
    private List<Comment> comments;

    // Fully cascaded (unlike tagList above) - a PostMedia row belongs to exactly one post and
    // has no meaning outside it, so ALL + orphanRemoval is correct here (deleting/replacing the
    // list deletes the orphaned rows too), where the same cascade on tagList would be the bug
    // tagList's own comment describes. PostServiceImpl replaces this list wholesale on
    // save/update (media.clear() + media.addAll(...)) rather than diffing individual entries -
    // simpler, and cheap at gallery-sized counts (a handful of rows per post).
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @BatchSize(size = 20)
    @OrderBy("position ASC")
    private List<PostMedia> media = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public List<PostMedia> getMedia() {
        return media;
    }

    public void setMedia(List<PostMedia> media) {
        this.media = media;
    }

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

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}
