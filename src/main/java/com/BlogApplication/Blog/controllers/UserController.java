package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.exceptions.DuplicateEmailException;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.services.UserService;
import com.BlogApplication.Blog.util.AvatarPresets;
import com.BlogApplication.Blog.util.CoverPresets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/registerUser")
    public String registerPage(Model model){
        model.addAttribute("userDto", new UserDto());
        model.addAttribute("avatarGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverGradients", AvatarPresets.GRADIENTS);
        model.addAttribute("coverScenes", CoverPresets.SCENES);
        return "register";
    }

    @PostMapping("/registerUser")
    public String createUser(@ModelAttribute UserDto userDto,
                              @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                              @RequestParam(value = "avatarMode", required = false) String avatarMode,
                              @RequestParam(value = "avatarPreset", required = false) String avatarPreset,
                              @RequestParam(value = "avatarSwatchIndex", required = false) Integer avatarSwatchIndex,
                              @RequestParam(value = "avatarHue", required = false) Integer avatarHue,
                              @RequestParam(value = "cover", required = false) MultipartFile cover,
                              @RequestParam(value = "coverMode", required = false) String coverMode,
                              @RequestParam(value = "coverPreset", required = false) String coverPreset,
                              @RequestParam(value = "coverSwatchIndex", required = false) Integer coverSwatchIndex,
                              @RequestParam(value = "coverHue", required = false) Integer coverHue,
                              Model model){
        try {
            userService.createUser(userDto, avatar, avatarMode, avatarPreset, avatarSwatchIndex, avatarHue,
                    cover, coverMode, coverPreset, coverSwatchIndex, coverHue);
        } catch (DuplicateEmailException ex) {
            userDto.setPassword("");
            model.addAttribute("userDto", userDto);
            model.addAttribute("error", ex.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
