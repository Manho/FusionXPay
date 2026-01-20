package com.fusionxpay.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdempotencyService
 */
@ExtendWith(MockitoExtension.class)
public class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    private static final String TEST_KEY = "test:event:123";
    private static final Duration TEST_TTL = Duration.ofDays(7);

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    // ==================== acquireProcessingLock Tests ====================

    @Test
    void testAcquireProcessingLock_Success() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(TEST_KEY), eq("processing"), eq(TEST_TTL)))
                .thenReturn(true);

        // When
        boolean result = idempotencyService.acquireProcessingLock(TEST_KEY, TEST_TTL);

        // Then
        assertTrue(result);
        verify(valueOperations).setIfAbsent(TEST_KEY, "processing", TEST_TTL);
    }

    @Test
    void testAcquireProcessingLock_AlreadyLocked() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(TEST_KEY), eq("processing"), eq(TEST_TTL)))
                .thenReturn(false);

        // When
        boolean result = idempotencyService.acquireProcessingLock(TEST_KEY, TEST_TTL);

        // Then
        assertFalse(result);
        verify(valueOperations).setIfAbsent(TEST_KEY, "processing", TEST_TTL);
    }

    @Test
    void testAcquireProcessingLock_NullResult() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(TEST_KEY), eq("processing"), eq(TEST_TTL)))
                .thenReturn(null);

        // When
        boolean result = idempotencyService.acquireProcessingLock(TEST_KEY, TEST_TTL);

        // Then
        assertFalse(result);
    }

    @Test
    void testAcquireProcessingLock_RedisConnectionFailure() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(TEST_KEY), eq("processing"), eq(TEST_TTL)))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then
        assertThrows(RedisConnectionFailureException.class, () -> {
            idempotencyService.acquireProcessingLock(TEST_KEY, TEST_TTL);
        });
    }

    @Test
    void testAcquireProcessingLock_UnexpectedException() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(TEST_KEY), eq("processing"), eq(TEST_TTL)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When
        boolean result = idempotencyService.acquireProcessingLock(TEST_KEY, TEST_TTL);

        // Then
        assertFalse(result);
    }

    // ==================== markAsCompleted Tests ====================

    @Test
    void testMarkAsCompleted_Success() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doNothing().when(valueOperations).set(eq(TEST_KEY), eq("completed"), eq(TEST_TTL));

        // When
        idempotencyService.markAsCompleted(TEST_KEY, TEST_TTL);

        // Then
        verify(valueOperations).set(TEST_KEY, "completed", TEST_TTL);
    }

    @Test
    void testMarkAsCompleted_RedisConnectionFailure() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(valueOperations).set(eq(TEST_KEY), eq("completed"), eq(TEST_TTL));

        // When & Then
        assertThrows(RedisConnectionFailureException.class, () -> {
            idempotencyService.markAsCompleted(TEST_KEY, TEST_TTL);
        });
    }

    @Test
    void testMarkAsCompleted_UnexpectedException() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Unexpected error"))
                .when(valueOperations).set(eq(TEST_KEY), eq("completed"), eq(TEST_TTL));

        // When - should not throw, just log the error
        assertDoesNotThrow(() -> {
            idempotencyService.markAsCompleted(TEST_KEY, TEST_TTL);
        });
    }

    // ==================== releaseLock Tests ====================

    @Test
    void testReleaseLock_Success() {
        // Given
        when(redisTemplate.delete(TEST_KEY)).thenReturn(true);

        // When
        idempotencyService.releaseLock(TEST_KEY);

        // Then
        verify(redisTemplate).delete(TEST_KEY);
    }

    @Test
    void testReleaseLock_KeyNotExists() {
        // Given
        when(redisTemplate.delete(TEST_KEY)).thenReturn(false);

        // When
        idempotencyService.releaseLock(TEST_KEY);

        // Then
        verify(redisTemplate).delete(TEST_KEY);
    }

    @Test
    void testReleaseLock_RedisConnectionFailure() {
        // Given
        when(redisTemplate.delete(TEST_KEY))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then
        assertThrows(RedisConnectionFailureException.class, () -> {
            idempotencyService.releaseLock(TEST_KEY);
        });
    }

    @Test
    void testReleaseLock_UnexpectedException() {
        // Given
        when(redisTemplate.delete(TEST_KEY))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When - should not throw, just log the error
        assertDoesNotThrow(() -> {
            idempotencyService.releaseLock(TEST_KEY);
        });
    }

    // ==================== getProcessingState Tests ====================

    @Test
    void testGetProcessingState_Processing() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TEST_KEY)).thenReturn("processing");

        // When
        String result = idempotencyService.getProcessingState(TEST_KEY);

        // Then
        assertEquals("processing", result);
        verify(valueOperations).get(TEST_KEY);
    }

    @Test
    void testGetProcessingState_Completed() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TEST_KEY)).thenReturn("completed");

        // When
        String result = idempotencyService.getProcessingState(TEST_KEY);

        // Then
        assertEquals("completed", result);
    }

    @Test
    void testGetProcessingState_NotFound() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TEST_KEY)).thenReturn(null);

        // When
        String result = idempotencyService.getProcessingState(TEST_KEY);

        // Then
        assertNull(result);
    }

    @Test
    void testGetProcessingState_RedisConnectionFailure() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TEST_KEY))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then
        assertThrows(RedisConnectionFailureException.class, () -> {
            idempotencyService.getProcessingState(TEST_KEY);
        });
    }

    @Test
    void testGetProcessingState_UnexpectedException() {
        // Given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(TEST_KEY))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When
        String result = idempotencyService.getProcessingState(TEST_KEY);

        // Then
        assertNull(result);
    }

    // ==================== Recovery Method Tests ====================

    @Test
    void testRecoverAcquireLock() {
        // Given
        RedisConnectionFailureException exception =
                new RedisConnectionFailureException("Connection refused");

        // When
        boolean result = idempotencyService.recoverAcquireLock(exception, TEST_KEY, TEST_TTL);

        // Then - should return true to ensure event processing continues
        assertTrue(result);
    }

    @Test
    void testRecoverGetState() {
        // Given
        RedisConnectionFailureException exception =
                new RedisConnectionFailureException("Connection refused");

        // When
        String result = idempotencyService.recoverGetState(exception, TEST_KEY);

        // Then - should return null to treat as not processed
        assertNull(result);
    }
}
