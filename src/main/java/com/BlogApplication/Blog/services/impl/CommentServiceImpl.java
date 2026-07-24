package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public void save(int postId, String content, String userEmail) {
        User user = findUser(userEmail);
        Post post = postRepo.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setName(user.getName());
        comment.setUser(user);
        comment.setPost(post);
        comment.setCreatedAt(LocalDateTime.now());

        Comment savedComment = commentRepo.save(comment);
        post.getComments().add(savedComment);
        postRepo.save(post);
    }

    @Override
    public void saveReply(int parentCommentId, String content, String userEmail) {
        User user = findUser(userEmail);
        Comment parentComment = commentRepo.findById(parentCommentId);
        if (parentComment == null) {
            throw new RuntimeException("Comment not found");
        }

        Comment reply = new Comment();
        reply.setContent(content);
        reply.setName(user.getName());
        reply.setUser(user);
        reply.setPost(parentComment.getPost());
        reply.setParent(parentComment);
        reply.setCreatedAt(LocalDateTime.now());

        Comment savedReply = commentRepo.save(reply);
        parentComment.getReplies().add(savedReply);
        commentRepo.save(parentComment);
    }

    private User findUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
    }
}
