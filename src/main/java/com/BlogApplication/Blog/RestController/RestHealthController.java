package com.BlogApplication.Blog.RestController;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Dedicated target for the ALB's health check (infra/terraform/beanstalk.tf) - previously pointed
// at /login, which worked (returns a real 200 with no auth) but had a real side effect: the ALB
// pings every 15s, forever, from a fresh cookie-less request each time, and /login renders a
// Thymeleaf form with a CSRF hidden field - which forces Spring Security to create a brand-new
// HttpSession just to hold that token. With sessions now Redis-backed (application.properties),
// every one of those health-check pings was writing a real, if harmless, session into Redis -
// confirmed live (245+ keys, all but one anonymous CSRF-token placeholders from health checks,
// none of them serving any purpose once created).
//
// @RestController returning a plain string, not a Thymeleaf view, is what actually fixes this -
// no template means nothing ever reads/materializes a CSRF token (Spring Security's default
// DeferredCsrfToken only computes and persists a token when something actually asks for one),
// so this endpoint touches neither CSRF nor the session at all. Confirmed via the same redis-cli
// DBSIZE check used throughout today's Redis work: verify this doesn't move at all across
// repeated hits, unlike /login's steady climb.
@RestController
public class RestHealthController {

    @GetMapping("/healthz")
    public ResponseEntity<String> healthz() {
        return ResponseEntity.ok("OK");
    }
}
