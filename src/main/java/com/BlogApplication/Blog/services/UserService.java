package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.UserDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserDto updateUser(UserDto user, Integer userId);
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
