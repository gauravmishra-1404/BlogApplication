package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.SelfFollowException;
import com.BlogApplication.Blog.models.Follow;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FollowSummary;
import com.BlogApplication.Blog.repositories.FollowRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowRepo followRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public FollowSummary toggle(String followerEmail, String followedUsername) {
        User follower = userRepo.findByEmail(followerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));
        User followed = userRepo.findByUsername(followedUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (follower.getId() == followed.getId()) {
            throw new SelfFollowException("You can't follow yourself.");
        }

        boolean following;
        Optional<Follow> existing = followRepo.findByFollowerIdAndFollowedId(follower.getId(), followed.getId());
        if (existing.isPresent()) {
            followRepo.delete(existing.get());
            following = false;
        } else {
            Follow follow = new Follow();
            follow.setFollower(follower);
            follow.setFollowed(followed);
            follow.setCreatedAt(LocalDateTime.now());
            try {
                followRepo.save(follow);
            } catch (DataIntegrityViolationException e) {
                // Two near-simultaneous clicks from the same user both passed the findBy check
                // before either inserted - the unique constraint caught it, and either way the
                // end state is "following", same as PostReactionServiceImpl's own race handling.
            }
            following = true;
        }

        long followerCount = followRepo.countByFollowedId(followed.getId());
        return new FollowSummary(following, followerCount);
    }
}
