package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.UnsupportedMediaTypeException;
import com.BlogApplication.Blog.models.PostMedia;
import com.BlogApplication.Blog.payloads.PresignedUpload;
import com.BlogApplication.Blog.services.MediaUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Active once AWS media storage is configured (aws.media.enabled=true) - see
// infra/terraform/media.tf for the bucket/CloudFront/IAM this depends on. No local/dev
// equivalent exists (unlike Cloudinary's LocalImageStorageService fallback) - this feature
// simply stays unavailable (the compose dropzone shows a config-needed message, see
// composeModal.js) until real AWS credentials are set, same as SnsNotificationPublisher's own
// aws.sqs.enabled gate.
@Service
@ConditionalOnProperty(prefix = "aws.media", name = "enabled", havingValue = "true")
public class S3MediaUploadService implements MediaUploadService {

    // contentType -> {PostMedia type constant, file extension} - the one place both the
    // allowlist AND the mapping from "what the browser sent" to "what we store" live, so
    // adding a new accepted format (say, image/avif) is a one-line change here, not a change
    // scattered across validation + extension-picking logic.
    private static final Map<String, String[]> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", new String[]{PostMedia.IMAGE, "jpg"},
            "image/png", new String[]{PostMedia.IMAGE, "png"},
            "image/webp", new String[]{PostMedia.IMAGE, "webp"},
            "image/gif", new String[]{PostMedia.IMAGE, "gif"},
            "video/mp4", new String[]{PostMedia.VIDEO, "mp4"},
            "video/webm", new String[]{PostMedia.VIDEO, "webm"},
            "video/quicktime", new String[]{PostMedia.VIDEO, "mov"}
    );

    // contentType -> extension only, no PostMedia type needed here - profile images are always
    // images, never video. A separate, narrower map from ALLOWED_CONTENT_TYPES above rather than
    // filtering it at call time, so this list can drift independently (e.g. if post media ever
    // adds a format that isn't a sane avatar/cover choice, or vice versa).
    private static final Map<String, String> ALLOWED_IMAGE_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private static final Set<String> ALLOWED_PROFILE_IMAGE_KINDS = Set.of("avatar", "cover");

    // Presigned PUT URLs (unlike a presigned POST with a policy document) can't carry an
    // enforced size condition - S3 will accept whatever the signed PUT request sends up to this
    // bucket's own account-level limits. The real size range (1-3MB per image, 5-100MB video) is
    // enforced client-side before upload even starts (see composeModal.js's MEDIA_MIN_*/MAX_*
    // constants) - a deliberate, documented simplification rather than the heavier presigned-
    // POST-with-policy-document flow, appropriate at this project's scale. A short expiry limits
    // how long a leaked/observed URL could be reused for anything at all - but it also has to
    // outlast the upload itself: the signature is checked once, when the PUT request lands at S3,
    // not when it started, so a slow connection uploading close to the 100MB video ceiling needs
    // real headroom. 20 minutes covers that (100MB even on a slow ~1 Mbps mobile upload is around
    // 13-14 minutes) while still keeping a leaked URL's usable window well short of a day.
    private static final Duration URL_EXPIRY = Duration.ofMinutes(20);

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.media.bucket:}")
    private String bucket;

    @Value("${aws.media.cdn-domain:}")
    private String cdnDomain;

    private S3Presigner presigner;

    private S3Presigner presigner() {
        if (presigner == null) {
            presigner = S3Presigner.builder().region(Region.of(region)).build();
        }
        return presigner;
    }

    @Override
    public PresignedUpload presign(String contentType, int ownerId) {
        String[] mapped = ALLOWED_CONTENT_TYPES.get(contentType);
        if (mapped == null) {
            throw new UnsupportedMediaTypeException("Unsupported file type: " + contentType);
        }
        String mediaType = mapped[0];
        String extension = mapped[1];

        // Never derived from the original filename - same reasoning
        // CloudinaryImageStorageService already documents for avatars: avoids path-traversal/
        // collision issues entirely, a fresh random key every time.
        String key = "posts/" + ownerId + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(URL_EXPIRY)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner().presignPutObject(presignRequest);

        return PresignedUpload.builder()
                .uploadUrl(presigned.url().toString())
                .publicUrl("https://" + cdnDomain + "/" + key)
                .mediaType(mediaType)
                .build();
    }

    @Override
    public PresignedUpload presignProfileImage(String contentType, int ownerId, String kind) {
        if (!ALLOWED_PROFILE_IMAGE_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unsupported profile image kind: " + kind);
        }
        String extension = ALLOWED_IMAGE_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new UnsupportedMediaTypeException("Unsupported file type: " + contentType);
        }

        // profiles/{ownerId}/{avatar|cover}/{uuid}.{ext} - a sibling prefix to posts/{ownerId}/...,
        // never overlapping with it (separate public-read statement, separate IAM resource in
        // media.tf/beanstalk.tf) and with avatar/cover keyed apart from each other so replacing
        // one can never accidentally clobber the other.
        String key = "profiles/" + ownerId + "/" + kind + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(URL_EXPIRY)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner().presignPutObject(presignRequest);

        return PresignedUpload.builder()
                .uploadUrl(presigned.url().toString())
                .publicUrl("https://" + cdnDomain + "/" + key)
                .mediaType(PostMedia.IMAGE)
                .build();
    }
}
