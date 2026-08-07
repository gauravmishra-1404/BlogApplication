package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.NotificationRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Only ever reads/writes the CALLER's own notifications - resolved from Authentication, never
// from a request parameter - same "no id to substitute" reasoning DraftController/
// BookmarkPageController already established for their own owner-only data.
@RestController
@RequestMapping("/api/notifications")
public class RestNotificationController {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private NotificationRepo notificationRepo;

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        User user = requireSelf(authentication);
        long count = user == null ? 0 : notificationRepo.countByRecipientIdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        User user = requireSelf(authentication);
        if (user != null) {
            notificationRepo.markAllReadForRecipient(user.getId());
        }
        return ResponseEntity.ok().build();
    }

    private User requireSelf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepo.findByEmail(authentication.getName()).orElse(null);
    }
}
