package com.BlogApplication.Blog.exceptions;

// Thrown by DisabledMediaUploadService whenever AWS media storage isn't configured yet
// (aws.media.enabled=false/unset - true today on Render, and in every local/dev environment) -
// RestMediaController catches this specifically and returns 503, distinct from
// UnsupportedMediaTypeException's 400 (a real request-content problem, not "this feature isn't
// turned on here").
public class MediaUploadUnavailableException extends RuntimeException {
    public MediaUploadUnavailableException(String message) {
        super(message);
    }
}
