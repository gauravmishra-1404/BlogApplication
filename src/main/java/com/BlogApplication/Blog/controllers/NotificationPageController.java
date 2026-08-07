package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Notification;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.NotificationRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// The bell icon's own page - a user's own notifications only, resolved from Authentication,
// same "no id to substitute" pattern as DraftController/BookmarkPageController.
@Controller
public class NotificationPageController {

    private static final int PAGE_SIZE = 20;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private NotificationRepo notificationRepo;

    @GetMapping("/notifications")
    public String myNotifications(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addNotificationsToModel(page, authentication, model);
        return "notificationsPage";
    }

    // "Load more" batch (js/loadMoreFeed.js, same click-to-fetch pattern BookmarkPageController's
    // /bookmarks/fragment already uses) - reuses fragments/notifRows.html for markup so the
    // initial page and this batch endpoint can't drift apart.
    @GetMapping("/notifications/fragment")
    public String myNotificationsFragment(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addNotificationsToModel(page, authentication, model);
        return "fragments/notifRows :: notifRows";
    }

    private void addNotificationsToModel(int page, Authentication authentication, Model model) {
        User viewer = requireSelf(authentication);
        Page<Notification> notificationPage = viewer == null
                ? Page.empty()
                : notificationRepo.findByRecipientIdOrderByCreatedAtDesc(viewer.getId(), PageRequest.of(page, PAGE_SIZE));

        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", notificationPage.hasNext());
    }

    // A row's own click target - marks it read, then sends the browser on to wherever it
    // actually points (a profile, a post). Ownership checked explicitly: the notification's
    // own recipient must match the caller, or this just bounces to /notifications with nothing
    // marked - the same direct-request IDOR guard every other owner-scoped mutation in this
    // app already has (see PostController.editPostByID's own doc comment for the precedent).
    @GetMapping("/notifications/{id}/open")
    public String openNotification(@PathVariable long id, Authentication authentication) {
        User viewer = requireSelf(authentication);
        if (viewer == null) {
            return "redirect:/login";
        }

        List<Notification> matches = notificationRepo.findById(id)
                .filter(n -> n.getRecipient() != null && n.getRecipient().getId() == viewer.getId())
                .map(List::of)
                .orElse(List.of());

        if (matches.isEmpty()) {
            return "redirect:/notifications";
        }

        Notification notification = matches.get(0);
        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepo.save(notification);
        }

        return notification.getTargetUrl() != null && !notification.getTargetUrl().isBlank()
                ? "redirect:" + notification.getTargetUrl()
                : "redirect:/notifications";
    }

    private User requireSelf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepo.findByEmail(authentication.getName()).orElse(null);
    }
}
