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

    // Same reasoning as root() above, and the actual gap a user found by hand: nothing ever
    // stopped an already-logged-in visitor from just typing /login into the address bar and
    // seeing the form again - "/login" was permitAll in SecurityConfig (it has to be, a logged-
    // out visitor needs to reach it), but permitAll only means "no authorization check", not
    // "redirect an authenticated user away". Not a real security hole - resubmitting the form
    // would just re-authenticate as the same user - but confusing UX a login page has no
    // business showing someone who's already past it.
    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        return isLoggedIn ? "redirect:/home" : "login";
    }

}
