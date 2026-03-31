package com.p2ps.auth.service;

import com.p2ps.exception.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Users registerUser(String email, String rawPassword, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already in use!");
        }

        String hashedPassword = passwordEncoder.encode(rawPassword);
        Users newUser = new Users(email, hashedPassword, firstName, lastName);
        return userRepository.save(newUser);
    }
}