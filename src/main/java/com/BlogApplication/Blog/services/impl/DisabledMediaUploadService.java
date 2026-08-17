package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.MediaUploadUnavailableException;
import com.BlogApplication.Blog.payloads.PresignedUpload;
import com.BlogApplication.Blog.services.MediaUploadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// Active whenever aws.media.enabled is false or unset - i.e. every local/dev environment today,
// and production too until infra/terraform/media.tf's resources are fully live (currently
// blocked on AWS's own CloudFront account-verification gate) and AWS_MEDIA_ENABLED=true is set.
// Same "always have a bean, even a stub one" reasoning LocalNotificationPublisher/
// ConsoleEmailService/LocalImageStorageService already establish - without this,
// RestMediaController's required @Autowired MediaUploadService has nothing to wire in at all
// whenever S3MediaUploadService's own @ConditionalOnProperty doesn't match, and the WHOLE APP
// fails to start (a missing bean is a startup-time failure, not a per-request one) - exactly
// what happened the first time this was tested locally.
@Service
@ConditionalOnProperty(prefix = "aws.media", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMediaUploadService implements MediaUploadService {

    @Override
    public PresignedUpload presign(String contentType, int ownerId) {
        throw new MediaUploadUnavailableException("Photo/video upload isn't available on this environment yet.");
    }

    @Override
    public PresignedUpload presignProfileImage(String contentType, int ownerId, String kind) {
        throw new MediaUploadUnavailableException("Photo upload isn't available on this environment yet.");
    }
}
