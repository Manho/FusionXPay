package com.fusionxpay.api.gateway.service;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private AuthRequest authRequest;
    private User user;

    @BeforeEach
    void setUp() {
        authRequest = new AuthRequest();
        authRequest.setUsername("testuser");
        authRequest.setPassword("testpassword");

        // Simulate a hashed password returned by the encoder
        String hashedPassword = "hashed_testpassword";
        user = User.builder()
                .id(1L)
                .username("testuser")
                .password(hashedPassword)
                .apiKey(UUID.randomUUID().toString())
                .roles("USER")
                .build();
    }

    @Test
    @DisplayName("Test register success")
    void register_Success() throws Exception {
        // Arrange
        when(userRepository.findByUsername(authRequest.getUsername())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(authRequest.getPassword())).thenReturn("hashed_testpassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        AuthResponse response = userService.register(authRequest);

        // Assert
        assertNotNull(response);
        assertEquals(user.getUsername(), response.getUsername());
        assertNotNull(response.getApiKey());
        verify(userRepository).findByUsername(authRequest.getUsername());
        verify(passwordEncoder).encode(authRequest.getPassword());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Test register duplicate user")
    void register_DuplicateUser() {
        // Arrange: simulate that the user already exists
        when(userRepository.findByUsername(authRequest.getUsername())).thenReturn(Optional.of(user));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> userService.register(authRequest));
        assertTrue(exception.getMessage().contains("User already exists"));
        verify(userRepository).findByUsername(authRequest.getUsername());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test login success")
    void login_Success() throws Exception {
        // Arrange
        when(userRepository.findByUsername(authRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authRequest.getPassword(), user.getPassword())).thenReturn(true);

        // Act
        AuthResponse response = userService.login(authRequest);

        // Assert
        assertNotNull(response);
        assertEquals(user.getUsername(), response.getUsername());
        assertEquals(user.getApiKey(), response.getApiKey());
        verify(userRepository).findByUsername(authRequest.getUsername());
        verify(passwordEncoder).matches(authRequest.getPassword(), user.getPassword());
    }

    @Test
    @DisplayName("Test login failure due to wrong password")
    void login_Failure_WrongPassword() {
        // Arrange
        when(userRepository.findByUsername(authRequest.getUsername())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(authRequest.getPassword(), user.getPassword())).thenReturn(false);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> userService.login(authRequest));
        assertTrue(exception.getMessage().contains("Invalid credentials"));
        verify(userRepository).findByUsername(authRequest.getUsername());
        verify(passwordEncoder).matches(authRequest.getPassword(), user.getPassword());
    }

    @Test
    @DisplayName("Test getUserByApiKey returns user")
    void getUserByApiKey_Found() {
        // Arrange
        when(userRepository.findByApiKey(user.getApiKey())).thenReturn(Optional.of(user));

        // Act
        Optional<User> result = userService.getUserByApiKey(user.getApiKey());

        // Assert
        assertTrue(result.isPresent());
        assertEquals(user.getUsername(), result.get().getUsername());
        verify(userRepository).findByApiKey(user.getApiKey());
    }

    @Test
    @DisplayName("Test getUserByApiKey returns empty")
    void getUserByApiKey_NotFound() {
        // Arrange
        String nonExistentKey = "nonexistent-api-key";
        when(userRepository.findByApiKey(nonExistentKey)).thenReturn(Optional.empty());

        // Act
        Optional<User> result = userService.getUserByApiKey(nonExistentKey);

        // Assert
        assertFalse(result.isPresent());
        verify(userRepository).findByApiKey(nonExistentKey);
    }
}
