package com.BlogApplication.Blog.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    // The bare domain (bodhsea.in, no path) had no mapping at all before this - confirmed live,
    // a Whitelabel 404 rather than landing anywhere useful, the single most natural thing a real
    // visitor types. Placeholder redirect straight to /login for now, per explicit direction -
    // a real marketing/landing page is planned separately; this just stops the bare domain from
    // 404ing in the meantime.
    @GetMapping("/")
    public String root(){
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(){
        return "login";
    }

}
