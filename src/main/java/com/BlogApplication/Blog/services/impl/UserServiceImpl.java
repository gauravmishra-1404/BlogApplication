package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.DuplicateEmailException;
import com.BlogApplication.Blog.exceptions.ResourceNotFoundException;
import com.BlogApplication.Blog.models.TokenPurpose;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.EmailService;
import com.BlogApplication.Blog.services.UserService;
import com.BlogApplication.Blog.services.VerificationTokenService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private EmailService emailService;

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
    public void createUser(UserDto userDto) {
        // Normalize so "Test@x.com" and "test@x.com" are treated as the same login id -
        // both at the uniqueness check here and in UserDetailsServiceImpl's lookup.
        String normalizedEmail = userDto.getEmail().trim().toLowerCase();

        if (userRepo.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encryptedPassword = encoder.encode(userDto.getPassword());

        User user = new User();
        user.setName(userDto.getName());
        user.setEmail(normalizedEmail);
        user.setPassword(encryptedPassword);
        user.setRole("ROLE_AUTHOR");
        user.setEmailVerified(false);

        userRepo.save(user);

        String token = verificationTokenService.createToken(user, TokenPurpose.VERIFY_EMAIL);
        emailService.sendVerificationEmail(user, token);
    }

}
