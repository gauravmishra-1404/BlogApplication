package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.PostReaction;
import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.PostReactionCount;
import com.BlogApplication.Blog.repositories.PostReactionRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.PostReactionService;
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
public class PostReactionServiceImpl implements PostReactionService {

    @Autowired
    private PostReactionRepo postReactionRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public ReactionSummary toggle(int postId, String userEmail, ReactionType type) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
        Post post = postRepo.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        Optional<PostReaction> existing = postReactionRepo.findByPostIdAndUserId(postId, user.getId());
        if (existing.isPresent()) {
            PostReaction reaction = existing.get();
            if (reaction.getReactionType() == type) {
                // Clicking the same button again - un-react.
                postReactionRepo.delete(reaction);
            } else {
                // Switching like <-> dislike.
                reaction.setReactionType(type);
                reaction.setUpdatedAt(LocalDateTime.now());
                postReactionRepo.save(reaction);
            }
        } else {
            PostReaction reaction = new PostReaction();
            reaction.setPost(post);
            reaction.setUser(user);
            reaction.setReactionType(type);
            reaction.setCreatedAt(LocalDateTime.now());
            reaction.setUpdatedAt(LocalDateTime.now());
            try {
                postReactionRepo.save(reaction);
            } catch (DataIntegrityViolationException e) {
                // Two near-simultaneous clicks from the same user both passed the findBy check
                // before either inserted - the unique constraint caught it, and the summary
                // below reflects whichever one actually landed.
            }
        }

        return getSummary(postId, userEmail);
    }

    @Override
    public ReactionSummary getSummary(int postId, String userEmail) {
        long likes = postReactionRepo.countByPostIdAndReactionType(postId, ReactionType.LIKE);
        long dislikes = postReactionRepo.countByPostIdAndReactionType(postId, ReactionType.DISLIKE);

        String userReaction = null;
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                userReaction = postReactionRepo.findByPostIdAndUserId(postId, user.getId())
                        .map(r -> r.getReactionType().name())
                        .orElse(null);
            }
        }

        return new ReactionSummary(likes, dislikes, userReaction);
    }

    @Override
    public Map<Integer, ReactionSummary> getSummaries(List<Integer> postIds, String userEmail) {
        if (postIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Long> likeCounts = new HashMap<>();
        Map<Integer, Long> dislikeCounts = new HashMap<>();
        for (PostReactionCount row : postReactionRepo.countGroupedByPostIds(postIds)) {
            if (row.getReactionType() == ReactionType.LIKE) {
                likeCounts.put(row.getPostId(), row.getCount());
            } else {
                dislikeCounts.put(row.getPostId(), row.getCount());
            }
        }

        Map<Integer, String> userReactions = new HashMap<>();
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                for (PostReaction reaction : postReactionRepo.findByPostIdInAndUserId(postIds, user.getId())) {
                    userReactions.put(reaction.getPost().getId(), reaction.getReactionType().name());
                }
            }
        }

        Map<Integer, ReactionSummary> result = new HashMap<>();
        for (Integer postId : postIds) {
            result.put(postId, new ReactionSummary(
                    likeCounts.getOrDefault(postId, 0L),
                    dislikeCounts.getOrDefault(postId, 0L),
                    userReactions.get(postId)
            ));
        }
        return result;
    }
}
