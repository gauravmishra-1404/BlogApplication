package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.services.ImageStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// Dev stand-in for CloudinaryImageStorageService - saves to a local folder (served via
// WebConfig's /uploads/** mapping) so avatar upload can be built and tested end-to-end without
// a Cloudinary account. NOT suitable for production: Render's filesystem is ephemeral, so
// anything written here is lost on every redeploy/restart - production must run with real
// Cloudinary credentials configured instead.
@Service
@ConditionalOnProperty(prefix = "cloudinary", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalImageStorageService implements ImageStorageService {

    private static final Path UPLOAD_DIR = Paths.get("uploads", "avatars");

    @Override
    public String store(MultipartFile file, String keyHint) throws IOException {
        Files.createDirectories(UPLOAD_DIR);

        String extension = extensionFor(file);
        String filename = UUID.randomUUID() + extension;

        Path target = UPLOAD_DIR.resolve(filename);
        Files.copy(file.getInputStream(), target);

        return "/uploads/avatars/" + filename;
    }

    // Any image content type is accepted (the upload endpoint validates content type starts
    // with "image/" before this is ever called) - this only decides what extension to save it
    // under, preferring the original filename's extension when there is one, since that covers
    // formats indefinitely without needing a switch statement per MIME type.
    private String extensionFor(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) {
                String ext = original.substring(dot).toLowerCase();
                if (ext.matches("\\.[a-z0-9]{2,5}")) {
                    return ext;
                }
            }
        }

        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            String subtype = contentType.substring("image/".length());
            if (subtype.equals("jpeg")) {
                return ".jpg";
            }
            if (subtype.equals("svg+xml")) {
                return ".svg";
            }
            return "." + subtype;
        }

        return ".bin";
    }
}
