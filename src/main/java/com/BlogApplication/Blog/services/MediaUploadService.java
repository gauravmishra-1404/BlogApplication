package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.PresignedUpload;

// Generates a short-lived, single-file upload permission slip - the browser uses it to PUT
// straight to S3, this app's own server never sees the file's bytes at all. Mirrors
// ImageStorageService's shape (one method, validated inputs) but a different mechanism:
// ImageStorageService uploads THROUGH this server (fine for small avatar images via Cloudinary);
// post media (images up to 25MB, video up to 200MB) is the reason a direct-to-S3 path exists at
// all - proxying that much data through a small Render instance would be slow and memory-heavy.
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
}
