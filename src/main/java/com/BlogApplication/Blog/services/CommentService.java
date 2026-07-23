package com.BlogApplication.Blog.services;

public interface CommentService {
    void save(int postId, String content, String userEmail);

    void saveReply(int parentCommentId, String content, String userEmail);
}
