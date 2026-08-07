package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.NotificationEvent;
import com.BlogApplication.Blog.services.NotificationPublisher;
import com.BlogApplication.Blog.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    // Spring resolves this to whichever NotificationPublisher bean's @ConditionalOnProperty
    // matched at startup (SqsNotificationPublisher or LocalNotificationPublisher) - this class
    // never checks which one it got, it just calls the interface method. If Spring ever found
    // BOTH conditions true at once it would fail to start with a "multiple beans of type
    // NotificationPublisher" error rather than silently picking one - a safety net, not
    // something to rely on avoiding by being careful.
    @Autowired
    private NotificationPublisher notificationPublisher;

    @Override
    public void notify(User recipient, String type, String actorName, String title, String body, String targetUrl) {
        if (recipient == null) {
            return;
        }
        notificationPublisher.publish(NotificationEvent.builder()
                .recipient(recipient)
                .type(type)
                .actorName(actorName)
                .title(title)
                .body(body)
                .targetUrl(targetUrl)
                .build());
    }
}
