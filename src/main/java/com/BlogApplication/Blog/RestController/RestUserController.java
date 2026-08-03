package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.payloads.UsernameAvailability;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class RestUserController {
    @Autowired
    private UserService userService;

    @Autowired
    private UserRepo userRepo;

    @GetMapping("/register")
    public ResponseEntity<UserDto> registerPage() {
        // Returning an empty UserDto for client-side form binding
        UserDto userDto = new UserDto();
        return new ResponseEntity<>(userDto, HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<String> createUser(@RequestBody UserDto userDto) {
        // This is a JSON endpoint (@RequestBody) - no multipart support here, so no avatar.
        // Upload one later via the profile page once that exists as an API too.
        userService.createUser(userDto, null);
        return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);
    }

    // Live availability check for the Personal info panel's username field (debounced from
    // js/personalInfoModal.js). Excludes the caller's OWN current username from "taken" - saving
    // your username back unchanged (or just re-checking after typing then deleting) shouldn't
    // show as unavailable.
    @GetMapping("/check-username")
    public ResponseEntity<UsernameAvailability> checkUsername(@RequestParam String username, Authentication authentication) {
        User currentUser = userRepo.findByEmail(authentication.getName()).orElse(null);
        int currentUserId = currentUser != null ? currentUser.getId() : -1;
        boolean available = userService.isUsernameAvailable(username.trim().toLowerCase(), currentUserId);
        return ResponseEntity.ok(new UsernameAvailability(available));
    }
}
