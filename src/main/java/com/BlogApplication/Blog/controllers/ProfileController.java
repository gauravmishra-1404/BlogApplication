package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ImageStorageService;
import com.BlogApplication.Blog.util.AvatarPresets;
import com.BlogApplication.Blog.util.CoverPresets;
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

import java.io.IOException;

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

    @GetMapping("/profile/{username}")
    public String viewProfile(@PathVariable String username, Authentication authentication, Model model) {
        User profileUser = userRepo.findByUsername(username).orElse(null);
        if (profileUser == null) {
            return "profileNotFound";
        }

        boolean isOwnProfile = authentication != null
                && authentication.isAuthenticated()
                && authentication.getName().equalsIgnoreCase(profileUser.getEmail());

        model.addAttribute("profileUser", profileUser);
        model.addAttribute("isOwnProfile", isOwnProfile);
        model.addAttribute("posts", postRepo.findVisibleByUser(profileUser));
        model.addAttribute("comments", commentRepo.findVisibleByUser(profileUser));
        model.addAttribute("avatarGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverScenes", CoverPresets.SCENES);
        return "profile";
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
