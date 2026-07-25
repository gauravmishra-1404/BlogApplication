package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.services.CommentReactionService;
import com.BlogApplication.Blog.services.PostReactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Instant like/dislike toggling for posts and comments, called via fetch() from
// js/reactions.js rather than a form POST + page reload. Not in SecurityConfig's permitAll
// list - reactions require login, unlike viewing - so these fall under anyRequest().authenticated().
// The like/dislike buttons themselves are only rendered for logged-in users (sec:authorize),
// so an anonymous visitor never actually reaches these endpoints in normal use.
@RestController
@RequestMapping("/api")
public class RestReactionController {

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentReactionService commentReactionService;

    @PostMapping("/posts/{id}/reactions/{type}")
    public ResponseEntity<ReactionSummary> togglePostReaction(@PathVariable int id, @PathVariable String type,
                                                              Authentication authentication) {
        ReactionType reactionType = parseType(type);
        if (reactionType == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(postReactionService.toggle(id, authentication.getName(), reactionType));
    }

    @PostMapping("/comments/{id}/reactions/{type}")
    public ResponseEntity<ReactionSummary> toggleCommentReaction(@PathVariable int id, @PathVariable String type,
                                                                 Authentication authentication) {
        ReactionType reactionType = parseType(type);
        if (reactionType == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(commentReactionService.toggle(id, authentication.getName(), reactionType));
    }

    private ReactionType parseType(String type) {
        try {
            return ReactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
