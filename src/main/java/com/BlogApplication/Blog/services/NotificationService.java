package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.User;

// The one entry point the rest of the app calls to fire a notification (a new follower, a
// comment, a reaction, etc.) - never talks to SQS or the database directly itself, that's
// NotificationPublisher's job (and there are two of those, see that interface's own comment).
public interface NotificationService {
    void notify(User recipient, String type, String actorName, String title, String body, String targetUrl);
}
