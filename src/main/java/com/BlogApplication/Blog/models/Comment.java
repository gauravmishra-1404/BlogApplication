package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name = "content", nullable = false)
    private String content;

    // The comments table is shared with a separate Q&A app on this same database (its own
    // comment threads on answers/questions use it too) - that app's rows require a NOT NULL
    // "name" column that this entity otherwise never touches. Every insert from this blog must
    // populate it (with the commenting user's display name) or Postgres rejects the row.
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Nullable Boolean, not a primitive: existing production rows predate this column and were
    // never backfilled (ddl-auto=update just adds the column, it doesn't set a value for old
    // rows), so the DB can hand back NULL here. A primitive boolean can't hold that and throws
    // on every load; Boolean absorbs it safely, and isEdited() below treats null as "not edited".
    @Column(name = "edited")
    private Boolean edited;

    // Soft delete, same reasoning as Post.deleted: this table is shared with a separate app,
    // and physically removing rows a reply might still reference is fragile. "Deleting" a
    // comment hides it (and its replies, recursively - see PostController.deleteComment)
    // rather than removing the row.
    @Column(name = "deleted")
    private Boolean deleted;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    private Post post;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER)
    private List<Comment> replies = new ArrayList<>();

    public Comment getParent() {
        return parent;
    }

    public void setParent(Comment parent) {
        this.parent = parent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public boolean isEdited() {
        return Boolean.TRUE.equals(edited);
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public void setReplies(List<Comment> replies) {
        this.replies = replies;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    // Used by commentNode.html to recurse into replies - filters out soft-deleted ones so a
    // deleted comment (and everything under it, per the recursive delete) disappears from the
    // rendered thread without needing the service layer to rebuild a filtered tree.
    public List<Comment> getVisibleReplies() {
        return replies.stream().filter(reply -> !reply.isDeleted()).toList();
    }
}
