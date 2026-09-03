package com.BlogApplication.Blog.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// The static, logged-out information pages linked from the landing page's footer: privacy policy,
// terms, security, and contact. Their own controller rather than more methods on LoginController -
// that one owns the "/" and "/login" entry points into the app, and these aren't part of that flow
// at all, they're public documents that happen to be served by the same application.
//
// No service layer and no model attributes on purpose: these templates are static content, so
// there is genuinely nothing to resolve per request. They must also be reachable logged-out, which
// means each path needs to be in SecurityConfig's permitAll list - a page linked from the footer
// of the landing page that redirects a logged-out visitor to /login would be worse than not
// linking it at all.
@Controller
public class PublicPagesController {

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/security")
    public String security() {
        return "security";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }
}
