package com.BlogApplication.Blog.services.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.BlogApplication.Blog.services.ImageStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

// Real image hosting - only active once real Cloudinary credentials are configured
// (see application.properties). Until then LocalImageStorageService handles uploads instead.
@Service
@ConditionalOnProperty(prefix = "cloudinary", name = "enabled", havingValue = "true")
public class CloudinaryImageStorageService implements ImageStorageService {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    private void init() {
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    @Override
    public String store(MultipartFile file, String keyHint) throws IOException {
        // public_id is deliberately NOT derived from the original filename - avoids path
        // traversal/collision issues entirely, Cloudinary just generates a fresh id per upload.
        // The 256x256 face-aware crop keeps every avatar a consistent square regardless of what
        // the user actually uploaded, without any image-processing code on our side.
        Transformation<?> avatarCrop = new Transformation<>()
                .width(256)
                .height(256)
                .crop("fill")
                .gravity("face");

        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "bodhsea/avatars",
                "transformation", avatarCrop
        ));
        return (String) result.get("secure_url");
    }
}
