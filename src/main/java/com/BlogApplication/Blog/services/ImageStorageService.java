package com.BlogApplication.Blog.services;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ImageStorageService {
    /**
     * Uploads an image and returns the URL it can be served from.
     *
     * @param file    the uploaded file (already validated: image content type, size limit)
     * @param keyHint a hint for naming/organizing the stored file (e.g. "avatar-42") - not
     *                trusted as-is, implementations sanitize/generate the real storage key
     */
    String store(MultipartFile file, String keyHint) throws IOException;
}
