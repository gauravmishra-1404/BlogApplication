package com.BlogApplication.Blog.util;

import com.BlogApplication.Blog.models.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

// Shared ownership check for post edit/delete - mirrors the logic PostController already used
// for comments (isAuthorizedForComment) so both the Thymeleaf controller and its REST mirror
// enforce the same rule instead of each re-deriving it: only the post's own author or an ADMIN
// may edit or delete it. The UI already hides the Edit/Delete buttons for non-owners, but that's
// cosmetic - this is the actual enforcement, since a direct request to the URL bypasses the UI.
public class PostAuthorization {

    private PostAuthorization() {
    }

    public static boolean isOwnerOrAdmin(Authentication authentication, User postOwner) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        String ownerEmail = postOwner != null ? postOwner.getEmail() : null;

        return isAdmin || (ownerEmail != null && ownerEmail.equals(authentication.getName()));
    }
}
