package com.p2ps.auth;

import com.p2ps.auth.model.Users;
import com.p2ps.auth.repository.UserRepository;
import com.p2ps.auth.service.UserService;
import com.p2ps.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final String email = "test@example.com";
    private final String password = "Password123";
    private final String firstName = "Andrei";
    private final String lastName = "Popescu";

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");
        when(userRepository.save(any(Users.class))).thenAnswer(i -> i.getArguments()[0]);

        Users savedUser = userService.registerUser(email, password, firstName, lastName);

        assertNotNull(savedUser);
        assertEquals(email, savedUser.getEmail());
        verify(userRepository, times(1)).save(any(Users.class));
    }

    @Test
    void registerUser_ThrowsException_WhenEmailExists() {
        when(userRepository.existsByEmail(email)).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.registerUser(email, password, firstName, lastName);
        });

        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void registerUser_HandlesDataIntegrityViolation() {
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashed");
        when(userRepository.save(any(Users.class))).thenThrow(new DataIntegrityViolationException("Duplicate"));

        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.registerUser(email, password, firstName, lastName);
        });
    }

    @Test
    void loadUserByUsername_Success() {
        Users user = new Users(email, "hashedPassword", firstName, lastName);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserDetails userDetails = userService.loadUserByUsername(email);

        assertNotNull(userDetails);
        assertEquals(email, userDetails.getUsername());
    }

    @Test
    void loadUserByUsername_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            userService.loadUserByUsername("nonexistent@example.com");
        });
    }
}