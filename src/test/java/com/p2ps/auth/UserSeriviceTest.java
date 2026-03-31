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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private String email = "test@example.com";
    private String password = "Password123";
    private String firstName = "Andrei";
    private String lastName = "Popescu";

    @Test
    void registerUser_Success() {
        // GIVEN
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");
        when(userRepository.save(any(Users.class))).thenAnswer(i -> i.getArguments()[0]);

        // WHEN
        Users savedUser = userService.registerUser(email, password, firstName, lastName);

        // THEN
        assertNotNull(savedUser);
        assertEquals(email, savedUser.getEmail());
        verify(userRepository, times(1)).save(any(Users.class));
    }

    @Test
    void registerUser_ThrowsException_WhenEmailExists() {
        // GIVEN
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // WHEN & THEN
        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.registerUser(email, password, firstName, lastName);
        });

        verify(userRepository, never()).save(any(Users.class));
    }

    @Test
    void registerUser_HandlesDataIntegrityViolation() {
        // GIVEN
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashed");
        when(userRepository.save(any(Users.class))).thenThrow(new DataIntegrityViolationException("Duplicate"));

        // WHEN & THEN
        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.registerUser(email, password, firstName, lastName);
        });
    }
}