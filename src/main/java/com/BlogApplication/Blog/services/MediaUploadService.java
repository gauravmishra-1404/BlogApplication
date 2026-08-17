package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.PresignedUpload;

// Generates a short-lived, single-file upload permission slip - the browser uses it to PUT
// straight to S3, this app's own server never sees the file's bytes at all. Started as
// post-media-only (images up to 25MB, video up to 200MB - proxying that through a small
// instance would be slow and memory-heavy); avatar/cover images joined this same direct-to-S3
// mechanism later (see presignProfileImage below) once ImageStorageService's Cloudinary path was
// retired for logged-in profile edits - registration itself still uses presets/color only, no
// upload, so it never calls either presign method (see register.html's identity picker).
public interface MediaUploadService {
    /**
     * @param contentType the file's MIME type, as reported by the browser - validated against
     *                     an allowlist before any URL is generated (see S3MediaUploadService)
     * @param ownerId      the uploading user's id, used only to namespace the generated S3 key
     *                     (posts/{ownerId}/{uuid}.{ext}) - never trusted for authorization on
     *                     its own, the caller (RestMediaController) already required a real
     *                     logged-in Authentication before this is ever called
     */
    PresignedUpload presign(String contentType, int ownerId);

    /**
     * Same mechanism as {@link #presign}, a separate method rather than an overload because the
     * validation is genuinely different: images only (no video), and a different S3 prefix so
     * profile images and post media never collide or share a permission scope.
     *
     * @param contentType the file's MIME type - image types only, see S3MediaUploadService's
     *                     own allowlist
     * @param ownerId      the uploading user's id, namespaces the key
     *                     (profiles/{ownerId}/{kind}/{uuid}.{ext})
     * @param kind         "avatar" or "cover" - which slot on the profile this is for, also
     *                     folded into the S3 key so the two can never overwrite each other
     */
    PresignedUpload presignProfileImage(String contentType, int ownerId, String kind);
}
