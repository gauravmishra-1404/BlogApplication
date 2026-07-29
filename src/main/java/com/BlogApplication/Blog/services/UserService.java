package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.UserDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserDto updateUser(UserDto user, Integer userId);

    // Lazily backfills a username for accounts that predate the username feature (existing
    // verified users, including ones from the DB shared with the other app) - self-heals the
    // first time GlobalModelAttributes loads them, no batch migration needed.
    User ensureUsername(User user);
    //UserDto getUserById(Integer userId);
    List<UserDto> getAllUsers();

    void createUser(UserDto userDto, MultipartFile avatar);

    // "avatarMode"/"coverMode" are each "photo" (default, uses the matching file param),
    // "preset", or "color" - the same 3-way choice as the profile page's Profile Photo / Cover
    // Photo editors, offered at signup time too.
    void createUser(UserDto userDto, MultipartFile avatar, String avatarMode, String avatarPreset,
                     Integer avatarSwatchIndex, Integer avatarHue,
                     MultipartFile cover, String coverMode, String coverPreset,
                     Integer coverSwatchIndex, Integer coverHue);
    //void deleteUser(Integer userId);
}
