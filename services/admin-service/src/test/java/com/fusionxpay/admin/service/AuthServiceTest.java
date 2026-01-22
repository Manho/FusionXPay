package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.LoginRequest;
import com.fusionxpay.admin.dto.LoginResponse;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import com.fusionxpay.admin.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantCode("MCH001")
                .merchantName("Test Merchant")
                .email("test@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(MerchantRole.MERCHANT)
                .status(MerchantStatus.ACTIVE)
                .build();
    }

    @Test
    void login_Success_ReturnsLoginResponse() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(merchantRepository.findByEmailAndStatus(request.getEmail(), MerchantStatus.ACTIVE))
                .thenReturn(Optional.of(testMerchant));
        when(jwtTokenProvider.generateToken(testMerchant.getId(), testMerchant.getEmail(), testMerchant.getRole().name()))
                .thenReturn("jwt.token.here");
        when(jwtTokenProvider.getExpirationInSeconds())
                .thenReturn(86400L);

        // When
        LoginResponse response = authService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt.token.here");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getMerchant()).isNotNull();
        assertThat(response.getMerchant().getEmail()).isEqualTo("test@example.com");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(merchantRepository).findByEmailAndStatus(request.getEmail(), MerchantStatus.ACTIVE);
    }

    @Test
    void login_InvalidCredentials_ThrowsBadCredentialsException() {
        // Given
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_MerchantNotFound_ThrowsBadCredentialsException() {
        // Given
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password");
        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(merchantRepository.findByEmailAndStatus(request.getEmail(), MerchantStatus.ACTIVE))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void getMerchantById_Success_ReturnsMerchantInfo() {
        // Given
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(testMerchant));

        // When
        MerchantInfo result = authService.getMerchantById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getMerchantCode()).isEqualTo("MCH001");
    }

    @Test
    void getMerchantById_NotFound_ThrowsException() {
        // Given
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> authService.getMerchantById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    void getMerchantByEmail_Success_ReturnsMerchantInfo() {
        // Given
        when(merchantRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testMerchant));

        // When
        MerchantInfo result = authService.getMerchantByEmail("test@example.com");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getRole()).isEqualTo(MerchantRole.MERCHANT);
    }
}
