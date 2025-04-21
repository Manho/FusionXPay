package com.fusionxpay.api.gateway.controller;

import com.fusionxpay.api.gateway.dto.AuthRequest;
import com.fusionxpay.api.gateway.dto.AuthResponse;
import com.fusionxpay.api.gateway.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private AuthRequest authRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        // Setup test data
        authRequest = new AuthRequest();
        authRequest.setUsername("testuser");
        authRequest.setPassword("testpassword");

        authResponse = AuthResponse.builder()
                .username("testuser")
                .apiKey(UUID.randomUUID().toString())
                .build();
    }

    @Test
    @DisplayName("Test successful user registration")
    void registerSuccess() throws Exception {
        when(userService.register(any(AuthRequest.class))).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.register(authRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(authResponse.getUsername(), response.getBody().getUsername());
        assertEquals(authResponse.getApiKey(), response.getBody().getApiKey());
    }

    @Test
    @DisplayName("Test registration with duplicate username")
    void registerDuplicateUsername() throws Exception {
        when(userService.register(any(AuthRequest.class))).thenThrow(new Exception("User already exists"));

        ResponseEntity<AuthResponse> response = authController.register(authRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Test successful user login")
    void loginSuccess() throws Exception {
        when(userService.login(any(AuthRequest.class))).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.login(authRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(authResponse.getUsername(), response.getBody().getUsername());
        assertEquals(authResponse.getApiKey(), response.getBody().getApiKey());
    }

    @Test
    @DisplayName("Test login with invalid credentials")
    void loginInvalidCredentials() throws Exception {
        when(userService.login(any(AuthRequest.class))).thenThrow(new Exception("Invalid credentials"));

        ResponseEntity<AuthResponse> response = authController.login(authRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNull(response.getBody());
    }
}
