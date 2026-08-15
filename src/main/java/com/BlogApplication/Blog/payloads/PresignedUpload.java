package com.BlogApplication.Blog.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Response body for POST /api/media/presign (RestMediaController) - everything the browser
// needs to upload one file straight to S3 and then know what to save once it succeeds.
//   @Getter - generates getUploadUrl()/getPublicUrl()/getMediaType(), same as any getter you'd
//     write by hand; Jackson (the JSON serializer) calls these to build the response body.
//   @Builder - fluent PresignedUpload.builder().uploadUrl(...).build() construction.
//   @AllArgsConstructor/@NoArgsConstructor - required for @Builder to have a constructor to
//     call, and for Jackson to be able to construct/deserialize this type if ever needed.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresignedUpload {
    // Short-lived (5 minutes) S3 PUT URL - the browser PUTs the raw file bytes here directly,
    // never through this app's own server.
    private String uploadUrl;

    // The CloudFront URL to save on the post once the PUT above succeeds - what actually gets
    // stored in PostMedia.mediaUrl and rendered to every future viewer.
    private String publicUrl;

    // "IMAGE" or "VIDEO" (PostMedia.IMAGE/VIDEO) - echoed back so the client doesn't have to
    // re-derive it from the content-type it already sent.
    private String mediaType;
}
