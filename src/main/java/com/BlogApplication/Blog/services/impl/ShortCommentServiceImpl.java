package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.ShortComment;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.ShortCommentCount;
import com.BlogApplication.Blog.repositories.ShortCommentRepo;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ShortCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Mirrors CommentServiceImpl exactly, against Shorts instead of Posts. No setName() call - unlike
// Comment, ShortComment doesn't carry the shared-Q&A-app "name" column (see ShortComment.java's
// own comment).
@Service
public class ShortCommentServiceImpl implements ShortCommentService {
    @Autowired
    private ShortCommentRepo shortCommentRepo;

    @Autowired
    private ShortRepo shortRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public void save(int shortId, String content, String userEmail) {
        User user = findUser(userEmail);
        ShortVideo shortVideo = shortRepo.findById(shortId).orElseThrow(() -> new RuntimeException("Short not found"));

        ShortComment comment = new ShortComment();
        comment.setContent(content);
        comment.setUser(user);
        comment.setShortVideo(shortVideo);
        comment.setCreatedAt(LocalDateTime.now());

        shortCommentRepo.save(comment);
    }

    @Override
    public void saveReply(int parentCommentId, String content, String userEmail) {
        User user = findUser(userEmail);
        ShortComment parentComment = shortCommentRepo.findById(parentCommentId);
        if (parentComment == null) {
            throw new RuntimeException("Comment not found");
        }

        ShortComment reply = new ShortComment();
        reply.setContent(content);
        reply.setUser(user);
        reply.setShortVideo(parentComment.getShortVideo());
        reply.setParent(parentComment);
        reply.setCreatedAt(LocalDateTime.now());

        ShortComment savedReply = shortCommentRepo.save(reply);
        parentComment.getReplies().add(savedReply);
        shortCommentRepo.save(parentComment);
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
    }

    @Override
    public Map<Integer, Long> countCommentsForShorts(List<Integer> shortIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (ShortCommentCount row : shortCommentRepo.countGroupedByShortIds(shortIds)) {
            counts.put(row.getShortId(), row.getCommentCount());
        }
        return counts;
    }
}
