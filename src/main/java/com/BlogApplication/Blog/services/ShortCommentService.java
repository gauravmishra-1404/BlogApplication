package com.BlogApplication.Blog.services;

import java.util.List;
import java.util.Map;

// Mirrors CommentService exactly, against Shorts instead of Posts.
public interface ShortCommentService {
    void save(int shortId, String content, String userEmail);

    void saveReply(int parentCommentId, String content, String userEmail);

    Map<Integer, Long> countCommentsForShorts(List<Integer> shortIds);
}
