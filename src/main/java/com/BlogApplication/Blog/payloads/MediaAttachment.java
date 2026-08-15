package com.BlogApplication.Blog.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One entry of the JSON array PostDto.media carries (see composeModal.js: after every file
// finishes uploading via the presign flow, its {url, type} pair gets pushed into a JS array
// that's JSON-stringified into the compose form's hidden "media" field right before submit).
// PostServiceImpl.resolveMedia() deserializes that JSON straight into a List<MediaAttachment>
// via Jackson - @NoArgsConstructor + @Setter is what makes that work (Jackson's default
// deserialization needs a no-args constructor and setters, it doesn't use @Builder unless
// separately configured to). @Builder/@AllArgsConstructor are kept anyway for constructing one
// of these directly in Java, e.g. in a test.
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MediaAttachment {
    private String url;
    private String type;
}
