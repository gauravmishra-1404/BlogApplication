package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.payloads.BookmarkSummary;
import com.BlogApplication.Blog.services.BookmarkService;
import com.BlogApplication.Blog.services.ShortBookmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Instant bookmark toggling, same shape as RestReactionController/RestFollowController - called
// via fetch() from js/bookmark.js rather than a form POST + reload. Not in SecurityConfig's
// permitAll list, so this falls under anyRequest().authenticated(); CSRF is exempt for the whole
// /api/** prefix already.
@RestController
@RequestMapping("/api")
public class RestBookmarkController {

    @Autowired
    private BookmarkService bookmarkService;

    @Autowired
    private ShortBookmarkService shortBookmarkService;

    @PostMapping("/posts/{id}/bookmark")
    public ResponseEntity<BookmarkSummary> toggleBookmark(@PathVariable int id, Authentication authentication) {
        // 404 for a missing/deleted/draft post - same "don't confirm it exists" reasoning
        // PostController.canViewPost already established, rather than a more specific error.
        return bookmarkService.toggle(authentication.getName(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Same shape as toggleBookmark above, against Shorts instead of Posts - js/bookmark.js was
    // genericized (data-target-type/data-target-id, same pattern reactions.js already used) to
    // call this URL when target-type="short", rather than duplicating a whole new JS file.
    @PostMapping("/shorts/{id}/bookmark")
    public ResponseEntity<BookmarkSummary> toggleShortBookmark(@PathVariable int id, Authentication authentication) {
        return shortBookmarkService.toggle(authentication.getName(), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
