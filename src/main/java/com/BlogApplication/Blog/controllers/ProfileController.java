package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.exceptions.DuplicateEmailException;
import com.BlogApplication.Blog.exceptions.IncorrectPasswordException;
import com.BlogApplication.Blog.exceptions.UsernameTakenException;
import com.BlogApplication.Blog.models.Follow;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FollowListEntry;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.FollowRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ImageStorageService;
import com.BlogApplication.Blog.services.UserService;
import com.BlogApplication.Blog.util.AvatarPresets;
import com.BlogApplication.Blog.util.CoverPresets;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB - matches registration's limit

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private FollowRepo followRepo;

    @Autowired
    private UserService userService;

    @GetMapping("/profile/{username}")
    public String viewProfile(@PathVariable String username, Authentication authentication, Model model) {
        User profileUser = userRepo.findByUsername(username).orElse(null);
        if (profileUser == null) {
            return "profileNotFound";
        }

        boolean isOwnProfile = authentication != null
                && authentication.isAuthenticated()
                && authentication.getName().equalsIgnoreCase(profileUser.getEmail());

        boolean isFollowing = false;
        if (!isOwnProfile && authentication != null && authentication.isAuthenticated()) {
            User viewer = userRepo.findByEmail(authentication.getName()).orElse(null);
            if (viewer != null) {
                isFollowing = followRepo.existsByFollowerIdAndFollowedId(viewer.getId(), profileUser.getId());
            }
        }

        model.addAttribute("profileUser", profileUser);
        model.addAttribute("isOwnProfile", isOwnProfile);
        model.addAttribute("isFollowing", isFollowing);
        model.addAttribute("followerCount", followRepo.countByFollowedId(profileUser.getId()));
        model.addAttribute("followingCount", followRepo.countByFollowerId(profileUser.getId()));
        model.addAttribute("posts", postRepo.findVisibleByUser(profileUser));
        model.addAttribute("comments", commentRepo.findRepliesReceivedByPostAuthor(profileUser));
        model.addAttribute("avatarGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverScenes", CoverPresets.SCENES);
        return "profile";
    }

    // Followers/Following list modal (js/followListModal.js) - fetched as a fragment and
    // injected into the modal body, same "fragment endpoint" shape as PostController's own
    // /post/{id}/modal. followingThisUser/self are both computed relative to the VIEWER, not
    // profileUser - a row's button always reflects the viewer's own relationship to that row's
    // user, exactly like clicking into anyone's followers list on Twitter/Instagram still lets
    // you follow/unfollow people directly from there.
    @GetMapping("/profile/{username}/followers")
    public String followersList(@PathVariable String username, Authentication authentication,
                                 Model model, HttpServletResponse response) {
        User profileUser = userRepo.findByUsername(username).orElse(null);
        if (profileUser == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "fragments/followListModal :: notFound";
        }
        List<Follow> rows = followRepo.findByFollowedIdOrderByCreatedAtDesc(profileUser.getId());
        model.addAttribute("entries", buildEntries(rows, true, authentication));
        return "fragments/followListModal :: followListRows";
    }

    @GetMapping("/profile/{username}/following")
    public String followingList(@PathVariable String username, Authentication authentication,
                                 Model model, HttpServletResponse response) {
        User profileUser = userRepo.findByUsername(username).orElse(null);
        if (profileUser == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "fragments/followListModal :: notFound";
        }
        List<Follow> rows = followRepo.findByFollowerIdOrderByCreatedAtDesc(profileUser.getId());
        model.addAttribute("entries", buildEntries(rows, false, authentication));
        return "fragments/followListModal :: followListRows";
    }

    // isFollowersList picks which side of each Follow row is "the other person" to list -
    // follower for a followers list, followed for a following list. followingThisUser/self are
    // batched via FollowRepo.findFollowedIdsAmong (same query the dashboard's Active-writers
    // widget already uses) rather than one existsBy call per row.
    private List<FollowListEntry> buildEntries(List<Follow> rows, boolean isFollowersList, Authentication authentication) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        List<Integer> otherUserIds = rows.stream()
                .map(f -> isFollowersList ? f.getFollower().getId() : f.getFollowed().getId())
                .toList();

        Set<Integer> viewerFollows = Set.of();
        if (viewer != null && !otherUserIds.isEmpty()) {
            viewerFollows = new HashSet<>(followRepo.findFollowedIdsAmong(viewer.getId(), otherUserIds));
        }

        List<FollowListEntry> entries = new ArrayList<>();
        for (Follow row : rows) {
            User otherUser = isFollowersList ? row.getFollower() : row.getFollowed();
            boolean self = viewer != null && viewer.getId() == otherUser.getId();
            boolean followingThisUser = viewerFollows.contains(otherUser.getId());
            entries.add(new FollowListEntry(otherUser, row.getCreatedAt(), followingThisUser, self));
        }
        return entries;
    }

    // Handles all 3 avatar modes from the same form. Only the fields for the submitted "mode"
    // are read - the others are ignored even if present, so a stale hidden input from a
    // different tab can't accidentally overwrite state. Gradients are never taken as raw CSS
    // from the client; "preset"/"color" always resolve through AvatarPresets from a validated
    // key/index/hue, matching the interactive mockup the user approved.
    @PostMapping("/profile/avatar")
    public String updateAvatar(@RequestParam String mode,
                                @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                                @RequestParam(value = "preset", required = false) String preset,
                                @RequestParam(value = "swatchIndex", required = false) Integer swatchIndex,
                                @RequestParam(value = "hue", required = false) Integer hue,
                                Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        User user = userRepo.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        switch (mode) {
            case "photo" -> {
                String url = storeImage(avatar, "avatar-" + user.getEmail());
                if (url == null) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setProfilePic(url);
                user.setAvatarType("photo");
            }
            case "preset" -> {
                String gradient = AvatarPresets.resolveGradient("preset", preset, null, null);
                if (gradient == null) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setAvatarType("preset");
                user.setAvatarPreset(preset);
                user.setAvatarGradient(gradient);
            }
            case "color" -> {
                String gradient = AvatarPresets.resolveGradient("color", null, swatchIndex, hue);
                if (gradient == null) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setAvatarType("color");
                user.setAvatarGradient(gradient);
            }
            default -> {
                return "redirect:/profile/" + user.getUsername();
            }
        }

        userRepo.save(user);
        return "redirect:/profile/" + user.getUsername();
    }

    // Same 3-way shape as updateAvatar, applied to the cover banner instead. "preset" resolves
    // through CoverPresets (a scene key, not a gradient) while "color" still shares
    // AvatarPresets.resolveGradient - the color-mixing math is identical for both surfaces.
    @PostMapping("/profile/cover")
    public String updateCover(@RequestParam String mode,
                               @RequestParam(value = "cover", required = false) MultipartFile cover,
                               @RequestParam(value = "preset", required = false) String preset,
                               @RequestParam(value = "swatchIndex", required = false) Integer swatchIndex,
                               @RequestParam(value = "hue", required = false) Integer hue,
                               Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        User user = userRepo.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        switch (mode) {
            case "photo" -> {
                String url = storeImage(cover, "cover-" + user.getEmail());
                if (url == null) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setCoverImage(url);
                user.setCoverType("photo");
            }
            case "preset" -> {
                if (!CoverPresets.isValidSceneKey(preset)) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setCoverType("preset");
                user.setCoverPreset(preset);
                user.setCoverGradient(null);
            }
            case "color" -> {
                String gradient = AvatarPresets.resolveGradient("color", null, swatchIndex, hue);
                if (gradient == null) {
                    return "redirect:/profile/" + user.getUsername();
                }
                user.setCoverType("color");
                user.setCoverGradient(gradient);
            }
            default -> {
                return "redirect:/profile/" + user.getUsername();
            }
        }

        userRepo.save(user);
        return "redirect:/profile/" + user.getUsername();
    }

    // --- Personal info panel: username / email / mobile, each edited independently. Every
    // endpoint below resolves the acting User from Authentication only - never from a request
    // parameter - so there is no id/username an attacker could substitute to touch someone
    // else's account, the same pattern /profile/avatar and /profile/cover already use. ---

    @PostMapping("/profile/personal-info/username")
    public String updateUsername(@RequestParam String username, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = requireSelf(authentication);
        if (user == null) {
            return "redirect:/login";
        }

        String normalized = username.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_]{3,30}")) {
            redirectAttributes.addFlashAttribute("error", "Usernames can only contain letters, numbers, and underscores (3-30 characters).");
            return "redirect:/profile/" + user.getUsername();
        }

        try {
            userService.updateUsername(user, normalized);
        } catch (UsernameTakenException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/profile/" + user.getUsername();
        }

        redirectAttributes.addFlashAttribute("message", "Username updated");
        return "redirect:/profile/" + normalized;
    }

    @PostMapping("/profile/personal-info/email")
    public String requestEmailChange(@RequestParam String newEmail, @RequestParam String currentPassword,
                                     Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = requireSelf(authentication);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            userService.requestEmailChange(user, newEmail, currentPassword);
            redirectAttributes.addFlashAttribute("message", "Confirmation link sent to " + newEmail.trim().toLowerCase());
        } catch (IncorrectPasswordException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DuplicateEmailException ex) {
            redirectAttributes.addFlashAttribute("error", "That email is already in use.");
        }
        return "redirect:/profile/" + user.getUsername();
    }

    @PostMapping("/profile/personal-info/mobile/request-otp")
    public String requestMobileOtp(@RequestParam String newMobile, @RequestParam String currentPassword,
                                   Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = requireSelf(authentication);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            userService.requestMobileOtp(user, newMobile, currentPassword);
            redirectAttributes.addFlashAttribute("message", "Code sent to " + newMobile.trim());
        } catch (IncorrectPasswordException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/profile/" + user.getUsername();
    }

    @PostMapping("/profile/personal-info/mobile/confirm-otp")
    public String confirmMobileOtp(@RequestParam String code, Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = requireSelf(authentication);
        if (user == null) {
            return "redirect:/login";
        }

        boolean confirmed = userService.confirmMobileOtp(user, code);
        redirectAttributes.addFlashAttribute(confirmed ? "message" : "error",
                confirmed ? "Mobile number verified" : "That code is incorrect or expired.");
        return "redirect:/profile/" + user.getUsername();
    }

    private User requireSelf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepo.findByEmail(authentication.getName()).orElse(null);
    }

    // Shared by both /profile/avatar and /profile/cover - an oversized file, a non-image
    // upload, or a storage-provider failure just means "keep whatever avatar/cover was already
    // set" (logged, never a hard error), the same tolerance registration's upload already has.
    private String storeImage(MultipartFile file, String keyHint) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            log.warn("Skipped image upload for {}: file exceeds {} bytes", keyHint, MAX_AVATAR_BYTES);
            return null;
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("Skipped image upload for {}: not an image ({})", keyHint, contentType);
            return null;
        }
        try {
            return imageStorageService.store(file, keyHint);
        } catch (IOException e) {
            log.warn("Image upload failed for {}", keyHint, e);
            return null;
        }
    }
}
