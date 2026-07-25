package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.CommentReaction;
import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.repositories.CommentReactionCount;
import com.BlogApplication.Blog.repositories.CommentReactionRepo;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CommentReactionServiceImpl implements CommentReactionService {

    @Autowired
    private CommentReactionRepo commentReactionRepo;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public ReactionSummary toggle(int commentId, String userEmail, ReactionType type) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
        Comment comment = commentRepo.findById(commentId);
        if (comment == null) {
            throw new RuntimeException("Comment not found");
        }

        Optional<CommentReaction> existing = commentReactionRepo.findByCommentIdAndUserId(commentId, user.getId());
        if (existing.isPresent()) {
            CommentReaction reaction = existing.get();
            if (reaction.getReactionType() == type) {
                commentReactionRepo.delete(reaction);
            } else {
                reaction.setReactionType(type);
                reaction.setUpdatedAt(LocalDateTime.now());
                commentReactionRepo.save(reaction);
            }
        } else {
            CommentReaction reaction = new CommentReaction();
            reaction.setComment(comment);
            reaction.setUser(user);
            reaction.setReactionType(type);
            reaction.setCreatedAt(LocalDateTime.now());
            reaction.setUpdatedAt(LocalDateTime.now());
            try {
                commentReactionRepo.save(reaction);
            } catch (DataIntegrityViolationException e) {
                // Same race as PostReactionServiceImpl.toggle - safe to ignore.
            }
        }

        return getSummary(commentId, userEmail);
    }

    @Override
    public ReactionSummary getSummary(int commentId, String userEmail) {
        long likes = commentReactionRepo.countByCommentIdAndReactionType(commentId, ReactionType.LIKE);
        long dislikes = commentReactionRepo.countByCommentIdAndReactionType(commentId, ReactionType.DISLIKE);

        String userReaction = null;
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                userReaction = commentReactionRepo.findByCommentIdAndUserId(commentId, user.getId())
                        .map(r -> r.getReactionType().name())
                        .orElse(null);
            }
        }

        return new ReactionSummary(likes, dislikes, userReaction);
    }

    @Override
    public Map<Integer, ReactionSummary> getSummaries(List<Integer> commentIds, String userEmail) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Long> likeCounts = new HashMap<>();
        Map<Integer, Long> dislikeCounts = new HashMap<>();
        for (CommentReactionCount row : commentReactionRepo.countGroupedByCommentIds(commentIds)) {
            if (row.getReactionType() == ReactionType.LIKE) {
                likeCounts.put(row.getCommentId(), row.getCount());
            } else {
                dislikeCounts.put(row.getCommentId(), row.getCount());
            }
        }

        Map<Integer, String> userReactions = new HashMap<>();
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                for (CommentReaction reaction : commentReactionRepo.findByCommentIdInAndUserId(commentIds, user.getId())) {
                    userReactions.put(reaction.getComment().getId(), reaction.getReactionType().name());
                }
            }
        }

        Map<Integer, ReactionSummary> result = new HashMap<>();
        for (Integer commentId : commentIds) {
            result.put(commentId, new ReactionSummary(
                    likeCounts.getOrDefault(commentId, 0L),
                    dislikeCounts.getOrDefault(commentId, 0L),
                    userReactions.get(commentId)
            ));
        }
        return result;
    }
}
