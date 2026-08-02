package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.User;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

// "X commented" line on the Following feed - only ever attached to a post whose own author
// ISN'T someone the viewer follows (otherwise the post already reads as followed-content
// without needing an explanation for why it's there).
@Value
@Builder
public class FollowedCommentAnnotation {
    User commenter;
    LocalDateTime commentedAt;
}
