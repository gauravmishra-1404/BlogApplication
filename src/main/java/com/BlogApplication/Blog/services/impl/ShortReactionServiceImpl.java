package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.models.ShortReaction;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.repositories.ShortReactionCount;
import com.BlogApplication.Blog.repositories.ShortReactionRepo;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ShortReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Mirrors PostReactionServiceImpl exactly, against Shorts instead of Posts.
@Service
public class ShortReactionServiceImpl implements ShortReactionService {

    @Autowired
    private ShortReactionRepo shortReactionRepo;

    @Autowired
    private ShortRepo shortRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public ReactionSummary toggle(int shortId, String userEmail, ReactionType type) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
        ShortVideo shortVideo = shortRepo.findById(shortId).orElseThrow(() -> new RuntimeException("Short not found"));

        Optional<ShortReaction> existing = shortReactionRepo.findByShortVideoIdAndUserId(shortId, user.getId());
        if (existing.isPresent()) {
            ShortReaction reaction = existing.get();
            if (reaction.getReactionType() == type) {
                shortReactionRepo.delete(reaction);
            } else {
                reaction.setReactionType(type);
                reaction.setUpdatedAt(LocalDateTime.now());
                shortReactionRepo.save(reaction);
            }
        } else {
            ShortReaction reaction = new ShortReaction();
            reaction.setShortVideo(shortVideo);
            reaction.setUser(user);
            reaction.setReactionType(type);
            reaction.setCreatedAt(LocalDateTime.now());
            reaction.setUpdatedAt(LocalDateTime.now());
            try {
                shortReactionRepo.save(reaction);
            } catch (DataIntegrityViolationException e) {
                // Race handling, same as PostReactionServiceImpl.
            }
        }

        return getSummary(shortId, userEmail);
    }

    @Override
    public ReactionSummary getSummary(int shortId, String userEmail) {
        long likes = shortReactionRepo.countByShortVideoIdAndReactionType(shortId, ReactionType.LIKE);
        long dislikes = shortReactionRepo.countByShortVideoIdAndReactionType(shortId, ReactionType.DISLIKE);

        String userReaction = null;
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                userReaction = shortReactionRepo.findByShortVideoIdAndUserId(shortId, user.getId())
                        .map(r -> r.getReactionType().name())
                        .orElse(null);
            }
        }

        return new ReactionSummary(likes, dislikes, userReaction);
    }

    @Override
    public Map<Integer, ReactionSummary> getSummaries(List<Integer> shortIds, String userEmail) {
        if (shortIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Long> likeCounts = new HashMap<>();
        Map<Integer, Long> dislikeCounts = new HashMap<>();
        for (ShortReactionCount row : shortReactionRepo.countGroupedByShortIds(shortIds)) {
            if (row.getReactionType() == ReactionType.LIKE) {
                likeCounts.put(row.getShortId(), row.getCount());
            } else {
                dislikeCounts.put(row.getShortId(), row.getCount());
            }
        }

        Map<Integer, String> userReactions = new HashMap<>();
        if (userEmail != null) {
            User user = userRepo.findByEmail(userEmail).orElse(null);
            if (user != null) {
                for (ShortReaction reaction : shortReactionRepo.findByShortVideoIdInAndUserId(shortIds, user.getId())) {
                    userReactions.put(reaction.getShortVideo().getId(), reaction.getReactionType().name());
                }
            }
        }

        Map<Integer, ReactionSummary> result = new HashMap<>();
        for (Integer shortId : shortIds) {
            result.put(shortId, new ReactionSummary(
                    likeCounts.getOrDefault(shortId, 0L),
                    dislikeCounts.getOrDefault(shortId, 0L),
                    userReactions.get(shortId)
            ));
        }
        return result;
    }
}
