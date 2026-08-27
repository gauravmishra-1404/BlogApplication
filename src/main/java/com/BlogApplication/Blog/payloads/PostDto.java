package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.PostMedia;
import com.BlogApplication.Blog.models.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;

public class PostDto {
    private int id;
    private String title;
    private String excerpt;
    private String content;
    private String author;
    private LocalDateTime publishedAt;
    private boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String tags;
    // Only meaningful alongside isPublished = false - see Post.scheduledAt's own comment.
    private LocalDateTime scheduledAt;
    // Write direction: raw JSON array from the compose form's hidden "media" field (e.g.
    // [{"url":"https://cdn.../posts/5/abc.jpg","type":"IMAGE"}]) - parsed by
    // PostServiceImpl.resolveMedia(), same "plain string on the DTO, resolved into real
    // entities in the service layer" pattern this class's own `tags` field already uses.
    private String mediaJson;
    // Read direction: the post's actual media rows, populated by PostServiceImpl.getPostById()
    // straight from Post.getMedia(), for viewPostByID.html/postModal.html to render (those bind
    // to PostDto via PostDetail, unlike postRows.html's feed cards which use the Post entity
    // directly and read post.media there instead - hence the different field name here).
    //
    // Neither this field nor mediaJson above is named plain "media" - Post.media is itself a
    // List<PostMedia>, and dtoToPost() maps PostDto -> Post by reflection (ModelMapper); a
    // same-named field pair (whether matching or mismatched in type) is exactly the kind of
    // silent-surprise risk worth naming around rather than trusting ModelMapper to do the right
    // thing automatically.
    private List<PostMedia> mediaList;
    private List<Comment> comments;
    private User user;
    private String role;
    private long viewCount;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getMediaJson() {
        return mediaJson;
    }

    public void setMediaJson(String mediaJson) {
        this.mediaJson = mediaJson;
    }

    public List<PostMedia> getMediaList() {
        return mediaList;
    }

    public void setMediaList(List<PostMedia> mediaList) {
        this.mediaList = mediaList;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
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

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean getPublished() {
        return isPublished;
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

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }
}
