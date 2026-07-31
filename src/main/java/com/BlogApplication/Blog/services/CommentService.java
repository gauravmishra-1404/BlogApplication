package com.BlogApplication.Blog.services;

import java.util.List;
import java.util.Map;

public interface CommentService {
    void save(int postId, String content, String userEmail);

    void saveReply(int parentCommentId, String content, String userEmail);

    Map<Integer, Long> countCommentsForPosts(List<Integer> postIds);
}
