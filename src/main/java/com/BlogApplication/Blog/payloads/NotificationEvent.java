package com.BlogApplication.Blog.payloads;

import com.BlogApplication.Blog.models.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// What the rest of the app hands to NotificationService.notify(...) - carries the full
// recipient User (not just an id), since both NotificationPublisher implementations need the
// recipient's email address (for the email channel) and neither should have to re-fetch it.
//
// Lombok annotations, since this is the project's standard DTO recipe going forward (see
// CLAUDE.md's PostDetail.java precedent) - each one generates code at COMPILE time (nothing
// added at runtime, no reflection magic happening later), you just never see the generated
// source:
//   @Getter               - generates getRecipient()/getType()/getTitle()/etc, one per field,
//                            so nothing below has to be hand-written.
//   @Builder               - generates a fluent builder: NotificationEvent.builder()
//                            .recipient(user).type("NEW_FOLLOWER")....build() - reads better
//                            than a 6-argument constructor call at the use site, since it's
//                            self-documenting which value is going into which field.
//   @AllArgsConstructor     - generates a constructor taking all 5 fields in declared order -
//                            @Builder actually uses THIS constructor internally to build the
//                            object, so it's required alongside @Builder, not redundant with it.
//   @NoArgsConstructor      - generates a public no-arg constructor - not used by this class's
//                            own code, but frameworks that construct objects reflectively
//                            (Jackson deserializing JSON, JPA hydrating an entity) often need
//                            one; harmless to include even where nothing needs it yet.
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationEvent {
    private User recipient;
    private String type;
    private String actorName;
    private String title;
    private String body;
    private String targetUrl;
}
