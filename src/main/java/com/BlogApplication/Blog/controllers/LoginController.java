package com.BlogApplication.Blog.controllers;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // The bare domain (bodhsea.in, no path) used to just redirect straight to /login - a
    // placeholder for exactly this, per that commit's own comment. Now the real marketing
    // landing page (templates/landing.html): a logged-out visitor sees it, an already-logged-in
    // user skips straight past it to /home instead of being pitched a "get started" page for a
    // product they're already inside - same isLoggedIn check GlobalModelAttributes.currentUser()
    // already uses (excluding the anonymous-authentication placeholder Spring Security installs
    // for a logged-out request, not just null).
    @GetMapping("/")
    public String root(Authentication authentication) {
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        return isLoggedIn ? "redirect:/home" : "landing";
    }

    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }

}
