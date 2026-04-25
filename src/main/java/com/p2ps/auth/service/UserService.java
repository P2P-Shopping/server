package com.p2ps.auth.service;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.exception.UserAlreadyExistsException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Users> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));


        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }

    @Transactional
    public Users registerUser(String email, String rawPassword, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already in use!");
        }

        try {
            String hashedPassword = passwordEncoder.encode(rawPassword);
            Users newUser = new Users(email, hashedPassword, firstName, lastName);
            newUser.setTokenVersion(0);
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException _) {
            throw new UserAlreadyExistsException("Email already in use!");
        }
    }

    @Transactional
    public void incrementTokenVersion(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
    }
}