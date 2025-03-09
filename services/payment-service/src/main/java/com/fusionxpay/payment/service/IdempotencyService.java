package com.fusionxpay.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for handling distributed idempotency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    
    /**
     * Attempts to acquire a distributed lock for processing an event
     * Uses retry mechanism for resilience against temporary Redis failures
     *
     * @param key the unique event key
     * @param ttl time-to-live for the lock
     * @return true if lock was acquired, false otherwise
     */
    @Retryable(
        value = {RedisConnectionFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public boolean acquireProcessingLock(String key, Duration ttl) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "processing", ttl);
            return Boolean.TRUE.equals(result);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failure when acquiring lock for {}, will retry", key);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error when acquiring lock for {}: {}", key, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Marks an event as successfully completed
     *
     * @param key the unique event key
     * @param ttl time-to-live for the record
     */
    @Retryable(
        value = {RedisConnectionFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public void markAsCompleted(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, "completed", ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failure when marking {} as completed, will retry", key);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error when marking {} as completed: {}", key, e.getMessage(), e);
        }
    }
    
    /**
     * Releases a processing lock to allow retry
     *
     * @param key the unique event key
     */
    @Retryable(
        value = {RedisConnectionFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public void releaseLock(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failure when releasing lock for {}, will retry", key);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error when releasing lock for {}: {}", key, e.getMessage(), e);
        }
    }
    
    /**
     * Checks if an event is already being processed or has been processed
     *
     * @param key the unique event key
     * @return processing state: null if not found, or the current state string
     */
    @Retryable(
        value = {RedisConnectionFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public String getProcessingState(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failure when getting state for {}, will retry", key);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error when getting state for {}: {}", key, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Recovery method if Redis operations fail after retries
     *
     * @return default fallback value
     */
    @Recover
    public boolean recoverAcquireLock(RedisConnectionFailureException e, String key, Duration ttl) {
        log.error("Redis operations failed after retries for key {}: {}", key, e.getMessage());
        // In case of Redis failure, we'll process the event anyway to ensure delivery
        // This risks duplicate processing but prevents missing an event
        return true;
    }
    
    @Recover
    public String recoverGetState(RedisConnectionFailureException e, String key) {
        log.error("Redis operations failed after retries when getting state for key {}: {}", key, e.getMessage());
        // When Redis is down, assume not processed to ensure delivery
        return null;
    }
}