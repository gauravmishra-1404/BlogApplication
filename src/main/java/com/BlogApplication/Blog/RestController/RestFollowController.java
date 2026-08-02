package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.exceptions.SelfFollowException;
import com.BlogApplication.Blog.payloads.FollowSummary;
import com.BlogApplication.Blog.services.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Instant follow/unfollow toggling, called via fetch() from js/follow.js rather than a form
// POST + page reload - same shape as RestReactionController. Not in SecurityConfig's permitAll
// list, so this falls under anyRequest().authenticated(); CSRF is exempt for the whole /api/**
// prefix already.
@RestController
@RequestMapping("/api")
public class RestFollowController {

    @Autowired
    private FollowService followService;

    @PostMapping("/users/{username}/follow")
    public ResponseEntity<FollowSummary> toggleFollow(@PathVariable String username, Authentication authentication) {
        try {
            return ResponseEntity.ok(followService.toggle(authentication.getName(), username));
        } catch (SelfFollowException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
