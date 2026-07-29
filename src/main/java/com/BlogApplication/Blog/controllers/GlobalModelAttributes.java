package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// Makes the logged-in User available as ${currentUser} on every page-rendering controller
// (scoped to @Controller, not @RestController - a JSON endpoint has no use for it) without
// each one having to look it up itself. Added for fragments/nav.html's profile dropdown, which
// needs the current user's avatar/name/username regardless of which controller rendered the
// page around it - the same "one shared place" reasoning as AvatarPresets.resolveGradient.
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private UserService userService;

    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        if (!isLoggedIn) {
            return null;
        }
        return userRepo.findByEmail(authentication.getName())
                .map(userService::ensureUsername)
                .orElse(null);
    }
}
