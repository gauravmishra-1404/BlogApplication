package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class RestUserController {
    @Autowired
    private UserService userService;

    @GetMapping("/register")
    public ResponseEntity<UserDto> registerPage() {
        // Returning an empty UserDto for client-side form binding
        UserDto userDto = new UserDto();
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<String> createUser(@RequestBody UserDto userDto) {
        System.out.println("User Password: " + userDto.getPassword());
        userService.createUser(userDto);
        return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);
    }
}
