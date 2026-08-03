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

    // --- Personal info panel (profile page) - username/email/mobile, each edited
    // independently. Every method here takes the User to act on directly rather than an id/
    // username parameter - the controller always resolves that User from the caller's own
    // Authentication, never from client input, so there is no id an attacker could substitute
    // to touch someone else's account. ---

    boolean isUsernameAvailable(String username, int currentUserId);

    void updateUsername(User user, String newUsername);

    // Sends a confirmation link to newEmail; the account's actual login email is untouched
    // until confirmEmailChange succeeds.
    void requestEmailChange(User user, String newEmail, String currentPassword);

    // True if the token was valid (right purpose, unused, unexpired) and the change was
    // applied; false otherwise. The caller can't tell WHY a token failed - same
    // don't-leak-more-than-necessary reasoning AuthRecoveryController's own token flows use.
    boolean confirmEmailChange(String token);

    void requestMobileOtp(User user, String newMobile, String currentPassword);

    // True if the code matched and wasn't expired; false otherwise.
    boolean confirmMobileOtp(User user, String code);
}
