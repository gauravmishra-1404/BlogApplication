package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.exceptions.InvalidPostException;
import com.BlogApplication.Blog.models.ShortComment;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.payloads.ShortDetail;
import com.BlogApplication.Blog.payloads.ShortDto;
import com.BlogApplication.Blog.payloads.ShortListing;
import com.BlogApplication.Blog.repositories.ShortBookmarkRepo;
import com.BlogApplication.Blog.repositories.ShortCommentRepo;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ShortCommentService;
import com.BlogApplication.Blog.services.ShortService;
import com.BlogApplication.Blog.services.ShortViewService;
import com.BlogApplication.Blog.services.VisitorIdentityService;
import com.BlogApplication.Blog.util.PostAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;

// Mirrors PostController's relevant shape (publish/republish/delete/comment CRUD, the
// dashboard/fragment feed split, the modal-detail view), scoped to Shorts and its own separate
// tables/services - see this feature's plan for why Shorts don't share Post's controller/tables.
@Controller
public class ShortsController {

    @Autowired
    private ShortService shortService;

    @Autowired
    private ShortCommentRepo shortCommentRepo;

    @Autowired
    private ShortCommentService shortCommentService;

    @Autowired
    private ShortViewService shortViewService;

    @Autowired
    private VisitorIdentityService visitorIdentityService;

    @Autowired
    private ShortRepo shortRepo;

    @Autowired
    private ShortBookmarkRepo shortBookmarkRepo;

    @Autowired
    private UserRepo userRepo;

    // The full immersive vertical-swipe feed page - same dashboard-shell chrome as
    // postDashboard.html (sidebar/right rail/FAB/compose modal all included), center column is a
    // scroll-snap container of shortsCard.html fragments. No entry-point id - starts at the plain
    // shared feed order, same as /shorts/{id} below once js/shortsFeed.js's own live URL-tracking
    // (history.replaceState as the viewer scrolls) settles on whichever card is first in view.
    @GetMapping("/shorts")
    public String shortsPage(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "5") int size,
                              Model model) {
        applyListingToModel(shortService.getShortsListing(page, size), model);
        return "shortsPage";
    }

    // A real per-Short URL - youtube.com/shorts/<id>'s entry-point half (see this feature's
    // plan/discussion): pins that one Short first, then continues into the same shared feed
    // order everyone else sees (no personalized queue to diverge into, unlike YouTube's own).
    // Same template as the plain /shorts above - the pinned id is just page 0's first item, not
    // a different page shape the client needs to know about.
    @GetMapping("/shorts/{id}")
    public String shortsPageAt(@PathVariable int id,
                                @RequestParam(defaultValue = "5") int size,
                                Model model) {
        applyListingToModel(shortService.getShortsListingStartingAt(id, size), model);
        return "shortsPage";
    }

    // js/shortsFeed.js's infinite-scroll fetch - same fragment-only response shape as
    // PostController.postsFragment. Always the plain shared order (never entry-point-pinned) -
    // by the time the viewer has scrolled far enough to trigger this, they're already past
    // whichever Short they entered on.
    @GetMapping("/shorts/fragment")
    public String shortsFragment(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "5") int size,
                                  Model model) {
        applyListingToModel(shortService.getShortsListing(page, size), model);
        return "fragments/shortsCards :: shortsCards";
    }

    private void applyListingToModel(ShortListing listing, Model model) {
        model.addAttribute("shorts", listing.getShorts());
        model.addAttribute("viewCounts", listing.getViewCounts());
        model.addAttribute("shortReactions", listing.getReactions());
        model.addAttribute("commentCounts", listing.getCommentCounts());
        model.addAttribute("currentPage", listing.getCurrentPage());
        model.addAttribute("hasNextPage", listing.isHasNextPage());
    }

    @PostMapping("/short/publish")
    public String publishShort(@ModelAttribute("shortDto") ShortDto shortDto, Principal principal, Model model,
                                RedirectAttributes redirectAttributes) {
        shortDto.setCreatedAt(LocalDateTime.now());
        try {
            shortService.save(shortDto, principal);
        } catch (InvalidPostException ex) {
            model.addAttribute("error", ex.getMessage());
            return "redirect:/shorts";
        }
        redirectAttributes.addFlashAttribute("message", shortDto.isPublished() ? "Short published" : "Saved as draft");
        return shortDto.isPublished() ? "redirect:/shorts" : "redirect:/drafts";
    }

    @PostMapping("/short/republish")
    public String republishShort(@ModelAttribute("shortDto") ShortDto shortDto, Authentication authentication,
                                  Model model, RedirectAttributes redirectAttributes) {
        ShortDto existing = shortService.getShortById(shortDto.getId());
        if (existing == null) {
            return "redirect:/shorts";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/shorts";
        }
        try {
            shortService.updateShortByID(shortDto, shortDto.getId());
        } catch (InvalidPostException ex) {
            model.addAttribute("error", ex.getMessage());
            return "redirect:/shorts";
        }
        redirectAttributes.addFlashAttribute("message", shortDto.isPublished() ? "Changes saved" : "Saved as draft");
        return shortDto.isPublished() ? "redirect:/shorts" : "redirect:/drafts";
    }

    @PostMapping("/shorts/delete")
    public String deleteShort(@RequestParam("id") int id, RedirectAttributes redirectAttributes, Authentication authentication) {
        ShortDto existing = shortService.getShortById(id);
        if (existing == null) {
            return "redirect:/shorts";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/shorts";
        }
        shortService.isDeleted(id);
        redirectAttributes.addFlashAttribute("message", "Short deleted successfully");
        return "redirect:/shorts";
    }

    // Comment-thread overlay opened by js/shortsFeed.js's comment-icon tap - mirrors
    // PostController.postModal exactly, against ShortDetail/shortModal instead of PostDetail/
    // postModal.
    @GetMapping("/short/{id}/modal")
    public String shortModal(@PathVariable int id, Model model, Authentication authentication,
                              HttpServletRequest request, HttpServletResponse response) {
        if (!canViewShort(id, authentication)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "fragments/shortModal :: notFound";
        }

        String viewerToken = visitorIdentityService.resolveViewerToken(authentication, request, response);
        shortViewService.recordView(id, viewerToken);

        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        String userEmail = isLoggedIn ? authentication.getName() : null;
        ShortDetail detail = shortService.getShortDetail(id, userEmail);
        if (detail == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "fragments/shortModal :: notFound";
        }

        ShortVideo shortVideo = shortRepo.findById(id).orElseThrow();

        model.addAttribute("shortVideo", detail.getShortVideo());
        model.addAttribute("reaction", detail.getReaction());
        model.addAttribute("commentReactions", detail.getCommentReactions());
        model.addAttribute("comments", shortVideo.getComments());
        model.addAttribute("isBookmarked", isBookmarked(id, authentication));

        return "fragments/shortModal :: shortModal";
    }

    private boolean canViewShort(int id, Authentication authentication) {
        ShortDto shortDto = shortService.getShortById(id);
        if (shortDto == null) {
            return false;
        }
        return shortDto.isPublished() || PostAuthorization.isOwnerOrAdmin(authentication, shortDto.getUser());
    }

    private boolean isBookmarked(int shortId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return userRepo.findByEmail(authentication.getName())
                .map(u -> shortBookmarkRepo.existsByUserIdAndShortVideoId(u.getId(), shortId))
                .orElse(false);
    }

    @PostMapping("/shorts/{id}/comments/add")
    public String addComment(@PathVariable("id") int shortId, @RequestParam String content, Authentication authentication) {
        if (!canViewShort(shortId, authentication)) {
            return "redirect:/shorts";
        }
        shortCommentService.save(shortId, content, authentication.getName());
        return "redirect:/shorts";
    }

    @PostMapping("/shorts/comments/delete/{id}")
    public String deleteComment(@PathVariable("id") int commentId, Authentication authentication) {
        ShortComment comment = shortCommentRepo.findById(commentId);
        if (comment == null) {
            return "redirect:/shorts";
        }
        ShortVideo shortVideo = comment.getShortVideo();
        if (shortVideo == null || shortVideo.isDeleted()) {
            return "redirect:/shorts";
        }
        if (!isAuthorizedForComment(authentication, comment)) {
            return "redirect:/shorts";
        }
        softDeleteCommentWithReplies(comment);
        return "redirect:/shorts";
    }

    private void softDeleteCommentWithReplies(ShortComment comment) {
        if (comment.getReplies() != null) {
            for (ShortComment reply : comment.getReplies()) {
                softDeleteCommentWithReplies(reply);
            }
        }
        comment.setDeleted(true);
        shortCommentRepo.save(comment);
    }

    @PostMapping("/shorts/comments/edit")
    public String editComment(@RequestParam("id") int commentId, @RequestParam("content") String content,
                               Authentication authentication) {
        ShortComment comment = shortCommentRepo.findById(commentId);
        if (comment == null || comment.getShortVideo() == null || comment.getShortVideo().isDeleted() || comment.isDeleted()) {
            return "redirect:/shorts";
        }
        if (!isCommentAuthorOrAdmin(authentication, comment)) {
            return "redirect:/shorts";
        }
        comment.setContent(content);
        comment.setEdited(true);
        shortCommentRepo.save(comment);
        return "redirect:/shorts";
    }

    @PostMapping("/shorts/comments/{commentId}/reply")
    public String saveReply(@PathVariable("commentId") int commentId, @RequestParam("content") String content,
                             Authentication authentication) {
        ShortComment parentComment = shortCommentRepo.findById(commentId);
        if (parentComment == null || parentComment.getShortVideo() == null
                || parentComment.getShortVideo().isDeleted() || parentComment.isDeleted()) {
            return "redirect:/shorts";
        }
        if (!canViewShort(parentComment.getShortVideo().getId(), authentication)) {
            return "redirect:/shorts";
        }
        shortCommentService.saveReply(commentId, content, authentication.getName());
        return "redirect:/shorts";
    }

    private boolean isCommentAuthorOrAdmin(Authentication authentication, ShortComment comment) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        String commentAuthorEmail = comment.getUser() != null ? comment.getUser().getEmail() : null;
        return isAdmin || (commentAuthorEmail != null && commentAuthorEmail.equals(authentication.getName()));
    }

    private boolean isAuthorizedForComment(Authentication authentication, ShortComment comment) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        String commentAuthorEmail = comment.getUser() != null ? comment.getUser().getEmail() : null;
        String shortOwnerEmail = comment.getShortVideo() != null && comment.getShortVideo().getUser() != null
                ? comment.getShortVideo().getUser().getEmail()
                : null;
        return isAdmin
                || (commentAuthorEmail != null && commentAuthorEmail.equals(authentication.getName()))
                || (shortOwnerEmail != null && shortOwnerEmail.equals(authentication.getName()));
    }
}
