package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.List;

// Deliberately its own entity/table, not a Post variant - see this feature's plan for the full
// reasoning (indexed FK joins cost the same either way; a separate table keeps Shorts' own
// likely-much-higher volume from bloating Post's indexes, and keeps every Short-interaction table
// isolated from the four existing, working Post-interaction tables). Always exactly one video -
// no PostMedia-style child collection, just a single column.
//
// Named ShortVideo, not Short - java.lang.Short (the boxed primitive wrapper) is implicitly
// imported into every Java file via java.lang.*, so a class literally named Short in this
// package would collide with it in any file that needs both, forcing fully-qualified names
// everywhere. The table itself is still named "shorts", matching the feature name used
// everywhere else (nav item, templates, endpoints).
@Entity
@Table(name = "shorts")
public class ShortVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    // Optional - a Short's content is the video, not the caption. Unlike Post.title, never
    // required.
    @Column(name = "caption", length = 2000)
    private String caption;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    // The three Phase 2 transcoding-pipeline columns - all nullable, all null on every row until
    // the transcode-worker Lambda processes it (existing rows stay this way forever if
    // aws.media.enabled is off, e.g. local dev - see S3MediaUploadService). Templates fall back
    // to the raw videoUrl whenever transcodedVideoUrl is null, so a Short is always playable
    // immediately on publish, never blocked on the pipeline finishing.
    @Column(name = "transcoded_video_url", length = 500)
    private String transcodedVideoUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // Plain String, not an enum - same "PostMedia.mediaType" precedent every other
    // content-kind/state discriminator in this codebase already follows. Values:
    // "PENDING"/"READY"/"FAILED", written only by the transcode-worker Lambda; the app itself
    // never sets this.
    @Column(name = "processing_status", length = 10)
    private String processingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Mirrors Post.comments exactly - ALL comments including nested replies (filtered to
    // top-level in the template via comment.parent == null, same as postModal.html already does).
    @OneToMany(mappedBy = "shortVideo", fetch = FetchType.EAGER)
    @BatchSize(size = 20)
    private List<ShortComment> comments;

    public List<ShortComment> getComments() {
        return comments;
    }

    public void setComments(List<ShortComment> comments) {
        this.comments = comments;
    }

    // Same null-safe-Boolean idiom as Post.isPublished/Post.deleted - see that file's own comment
    // for why (existing rows before a column existed can't be trusted to have it explicitly set).
    @Column(name = "is_published")
    private Boolean isPublished;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // Only meaningful while isPublished is false - see Post.scheduledAt's own comment, same idiom,
    // read by ScheduledShortPublisher's poller.
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted")
    private Boolean deleted;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getTranscodedVideoUrl() {
        return transcodedVideoUrl;
    }

    public void setTranscodedVideoUrl(String transcodedVideoUrl) {
        this.transcodedVideoUrl = transcodedVideoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
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

    public boolean getPublished() {
        return isPublished();
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
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
