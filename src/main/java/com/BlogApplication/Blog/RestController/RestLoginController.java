package com.BlogApplication.Blog.RestController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RestLoginController {

    @GetMapping("/login")
    public String loginPage(){
        return "Login endpoint hit"; // You could return a JSON object here as well.
    }

}
