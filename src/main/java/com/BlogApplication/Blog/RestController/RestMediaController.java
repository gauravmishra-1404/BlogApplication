package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.exceptions.MediaUploadUnavailableException;
import com.BlogApplication.Blog.exceptions.UnsupportedMediaTypeException;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PresignedUpload;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.MediaUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Called from js/composeModal.js the moment a file is chosen, BEFORE the post form itself is
// ever submitted - the browser presigns, uploads straight to S3, then carries the resulting
// CloudFront URL as a plain hidden field on the normal /post/publish or /post/republish submit
// (see PostDto.media). Not in SecurityConfig's permitAll list, so this requires a logged-in
// session same as every other /api/** endpoint; CSRF is exempt for the whole prefix already.
@RestController
@RequestMapping("/api/media")
public class RestMediaController {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private MediaUploadService mediaUploadService;

    @PostMapping("/presign")
    public ResponseEntity<PresignedUpload> presign(@RequestParam String contentType, Authentication authentication) {
        User user = userRepo.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(mediaUploadService.presign(contentType, user.getId()));
        } catch (UnsupportedMediaTypeException e) {
            return ResponseEntity.badRequest().build();
        } catch (MediaUploadUnavailableException e) {
            // 503, not 400 - this isn't a problem with what the client sent, it's that AWS
            // media storage isn't configured on this environment yet (DisabledMediaUploadService
            // is the active bean). composeModal.js surfaces this via the same toast used for
            // rejected files, worded as "not available right now" rather than a format/size error.
            return ResponseEntity.status(503).build();
        }
    }

    // Same mechanism as /presign, called from the profile page's avatar/cover editors
    // (js/profileImageUpload.js) instead of composeModal.js - never from register.html, which
    // offers presets/color only (no upload) since there's no session yet to authenticate this
    // call with. kind is "avatar" or "cover", validated in S3MediaUploadService.
    @PostMapping("/presign-profile-image")
    public ResponseEntity<PresignedUpload> presignProfileImage(@RequestParam String contentType,
                                                                 @RequestParam String kind,
                                                                 Authentication authentication) {
        User user = userRepo.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(mediaUploadService.presignProfileImage(contentType, user.getId(), kind));
        } catch (UnsupportedMediaTypeException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (MediaUploadUnavailableException e) {
            return ResponseEntity.status(503).build();
        }
    }

    // Same mechanism as /presign, called from composeModal.js's Short branch - video only, own
    // shorts/{ownerId}/... S3 prefix (see MediaUploadService.presignShortVideo's own comment).
    @PostMapping("/presign-short-video")
    public ResponseEntity<PresignedUpload> presignShortVideo(@RequestParam String contentType, Authentication authentication) {
        User user = userRepo.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(mediaUploadService.presignShortVideo(contentType, user.getId()));
        } catch (UnsupportedMediaTypeException e) {
            return ResponseEntity.badRequest().build();
        } catch (MediaUploadUnavailableException e) {
            return ResponseEntity.status(503).build();
        }
    }
}
