package com.BlogApplication.Blog.exceptions;

// Thrown by MediaUploadService.presign() when the requested content-type isn't on the
// image/video allowlist, or the caller asked for a size class this app doesn't offer -
// RestMediaController catches this and returns 400, same pattern SelfFollowException/
// RestFollowController already established.
public class UnsupportedMediaTypeException extends RuntimeException {
    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
