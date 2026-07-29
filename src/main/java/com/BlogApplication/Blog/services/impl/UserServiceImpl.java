package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.DuplicateEmailException;
import com.BlogApplication.Blog.exceptions.ResourceNotFoundException;
import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.EmailService;
import com.BlogApplication.Blog.services.ImageStorageService;
import com.BlogApplication.Blog.services.UserService;
import com.BlogApplication.Blog.services.VerificationTokenService;
import com.BlogApplication.Blog.util.AvatarPresets;
import com.BlogApplication.Blog.util.CoverPresets;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ImageStorageService imageStorageService;

    private User dtoToUser(UserDto userDto){
        User user = this.modelMapper.map(userDto , User.class);

//        user.setId(userDto.getId());
//        user.setName(userDto.getName());
//        user.setEmail(userDto.getEmail());
//        user.setPassword(userDto.getPassword());

        return user;
    }

    private UserDto userToDto(User user){
        UserDto userDto = this.modelMapper.map(user,UserDto.class);

//        userDto.setId(user.getId());
//        userDto.setName(user.getName());
//        userDto.setEmail(user.getEmail());
//        userDto.setPassword(user.getPassword());

        return userDto;
    }


    @Override
    public UserDto updateUser(UserDto userDto, Integer userId){
        User user = this.userRepo.findById(userId).orElseThrow(()->new ResourceNotFoundException("User","id",userId));
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        user.setPassword(userDto.getPassword());

        User updatedUser = this.userRepo.save(user);
        return this.userToDto(updatedUser);
    }

//    @Override
//    public UserDto getUserById(Integer userId){
//        return null;
//    }

    @Override
    public List<UserDto> getAllUsers(){
        List<User> users = this.userRepo.findAll();
        List<UserDto> userDtos = users.stream().map(user->this.userToDto(user)).collect(Collectors.toList());
        return userDtos;
    }

    @Override
    public void createUser(UserDto userDto, MultipartFile avatar) {
        createUser(userDto, avatar, "photo", null, null, null, null, "photo", null, null, null);
    }

    @Override
    public void createUser(UserDto userDto, MultipartFile avatar, String avatarMode, String avatarPreset,
                            Integer avatarSwatchIndex, Integer avatarHue,
                            MultipartFile cover, String coverMode, String coverPreset,
                            Integer coverSwatchIndex, Integer coverHue) {
        // Normalize so "Test@x.com" and "test@x.com" are treated as the same login id -
        // both at the uniqueness check here and in UserDetailsServiceImpl's lookup.
        String normalizedEmail = userDto.getEmail().trim().toLowerCase();

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encryptedPassword = encoder.encode(userDto.getPassword());

        var existing = userRepo.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            User existingUser = existing.get();
            if (existingUser.isEmailVerified()) {
                // A real duplicate - someone already owns and uses this verified account.
                throw new DuplicateEmailException(normalizedEmail);
            }
            // Never verified (link expired, email lost to a spam filter, network hiccup, etc.) -
            // treat re-registering as a retry: refresh their details from this submission and
            // send a fresh token, rather than trapping them behind "already registered" with no
            // way to recover an email they never got in the first place.
            existingUser.setName(userDto.getName());
            existingUser.setPassword(encryptedPassword);
            if (existingUser.getUsername() == null) {
                existingUser.setUsername(generateUniqueUsername(userDto.getName(), normalizedEmail));
            }
            applyAvatar(existingUser, avatar, avatarMode, avatarPreset, avatarSwatchIndex, avatarHue);
            applyCover(existingUser, cover, coverMode, coverPreset, coverSwatchIndex, coverHue);
            userRepo.save(existingUser);

            String token = verificationTokenService.createToken(existingUser, TokenPurpose.VERIFY_EMAIL);
            emailService.sendVerificationEmail(existingUser, token);
            return;
        }

        User user = new User();
        user.setName(userDto.getName());
        user.setEmail(normalizedEmail);
        user.setPassword(encryptedPassword);
        user.setRole("ROLE_AUTHOR");
        user.setEmailVerified(false);
        user.setUsername(generateUniqueUsername(userDto.getName(), normalizedEmail));
        user.setCreatedAt(LocalDateTime.now());
        applyAvatar(user, avatar, avatarMode, avatarPreset, avatarSwatchIndex, avatarHue);
        applyCover(user, cover, coverMode, coverPreset, coverSwatchIndex, coverHue);

        userRepo.save(user);

        String token = verificationTokenService.createToken(user, TokenPurpose.VERIFY_EMAIL);
        emailService.sendVerificationEmail(user, token);
    }

    @Override
    public User ensureUsername(User user) {
        if (user.getUsername() == null) {
            user.setUsername(generateUniqueUsername(user.getName(), user.getEmail()));
            userRepo.save(user);
        }
        return user;
    }

    // Derives a URL-safe handle from the display name (falling back to the email's local part
    // if the name is blank), then de-duplicates with a numeric suffix - "alice", "alice2",
    // "alice3"... - since /profile/{username} needs it unique the same way email already is.
    private String generateUniqueUsername(String name, String normalizedEmail) {
        String base = (name != null && !name.isBlank()) ? name : normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        base = base.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (userRepo.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    // Mirrors ProfileController.updateAvatar's 3-way choice (photo / preset / color), offered
    // at signup too. None of these ever block registration itself - an oversized/non-image
    // file, an invalid preset key, or a storage-provider failure just leaves the account on the
    // default initial-letter avatar, the same "log it, don't crash the request" tolerance
    // already used for email delivery failures.
    private void applyAvatar(User user, MultipartFile avatar, String avatarMode, String avatarPreset,
                              Integer avatarSwatchIndex, Integer avatarHue) {
        String mode = avatarMode == null ? "photo" : avatarMode;

        if ("preset".equals(mode) || "color".equals(mode)) {
            String gradient = AvatarPresets.resolveGradient(mode, avatarPreset, avatarSwatchIndex, avatarHue);
            if (gradient == null) {
                return;
            }
            user.setAvatarType(mode);
            user.setAvatarPreset("preset".equals(mode) ? avatarPreset : null);
            user.setAvatarGradient(gradient);
            return;
        }

        String url = storeImage(avatar, "avatar-" + user.getEmail());
        if (url == null) {
            return;
        }
        user.setProfilePic(url);
        user.setAvatarType("photo");
    }

    // Same 3-way choice as applyAvatar, mirroring ProfileController.updateCover - now offered
    // at signup too (upload/presets/color, not just presets like the picker's first cut).
    private void applyCover(User user, MultipartFile cover, String coverMode, String coverPreset,
                             Integer coverSwatchIndex, Integer coverHue) {
        String mode = coverMode == null ? "photo" : coverMode;

        if ("preset".equals(mode)) {
            if (!CoverPresets.isValidSceneKey(coverPreset)) {
                return;
            }
            user.setCoverType("preset");
            user.setCoverPreset(coverPreset);
            return;
        }
        if ("color".equals(mode)) {
            String gradient = AvatarPresets.resolveGradient("color", null, coverSwatchIndex, coverHue);
            if (gradient == null) {
                return;
            }
            user.setCoverType("color");
            user.setCoverGradient(gradient);
            return;
        }

        String url = storeImage(cover, "cover-" + user.getEmail());
        if (url == null) {
            return;
        }
        user.setCoverImage(url);
        user.setCoverType("photo");
    }

    // Shared by applyAvatar and applyCover - an oversized file, a non-image upload, or a
    // storage-provider failure just means "leave this avatar/cover unset" (logged, never a
    // hard error), the same tolerance the rest of registration already has.
    private String storeImage(MultipartFile file, String keyHint) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            log.warn("Skipped image upload for {}: file exceeds {} bytes", keyHint, MAX_AVATAR_BYTES);
            return null;
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            log.warn("Skipped image upload for {}: not an image ({})", keyHint, contentType);
            return null;
        }
        try {
            return imageStorageService.store(file, keyHint);
        } catch (IOException e) {
            log.warn("Image upload failed for {}", keyHint, e);
            return null;
        }
    }

}
