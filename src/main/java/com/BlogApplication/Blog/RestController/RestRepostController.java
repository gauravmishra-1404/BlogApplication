package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.payloads.RepostSummary;
import com.BlogApplication.Blog.services.RepostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Instant repost toggling, same shape as RestBookmarkController - called via fetch() from
// js/repost.js rather than a form POST + reload. Not in SecurityConfig's permitAll list, so this
// falls under anyRequest().authenticated(); CSRF is exempt for the whole /api/** prefix already.
@RestController
@RequestMapping("/api")
public class RestRepostController {

    @Autowired
    private RepostService repostService;

    @PostMapping("/posts/{id}/repost")
    public ResponseEntity<RepostSummary> toggleRepost(@PathVariable int id, Authentication authentication) {
        // 404 for a missing/deleted/draft post - same "don't confirm it exists" reasoning
        // PostController.canViewPost/RestBookmarkController already established.
        return repostService.toggle(authentication.getName(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
