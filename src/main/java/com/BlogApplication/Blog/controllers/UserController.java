package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.exceptions.DuplicateEmailException;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/registerUser")
    public String registerPage(Model model){
        model.addAttribute("userDto", new UserDto());
        return "register";
    }

    @PostMapping("/registerUser")
    public String createUser(@ModelAttribute UserDto userDto, Model model){
        try {
            userService.createUser(userDto);
        } catch (DuplicateEmailException ex) {
            userDto.setPassword("");
            model.addAttribute("userDto", userDto);
            model.addAttribute("error", ex.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
