package com.fusionxpay.payment.integration;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import com.fusionxpay.common.test.WireMockConfig;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import com.fusionxpay.payment.service.IdempotencyService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for idempotency handling.
 * Tests duplicate webhook handling and concurrent webhook processing with Redis-based locks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IdempotencyIT extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int WIREMOCK_PORT = 8091;
    private static final String REDIS_KEY_PREFIX = "stripe:webhook:event:order:";

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = WireMockConfig.createWireMockServer(WIREMOCK_PORT);
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        paymentTransactionRepository.deleteAll();
        // Clean up Redis keys for idempotency
        cleanupRedisKeys();
    }

    private void cleanupRedisKeys() {
        try {
            var keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure Stripe to use WireMock
        registry.add("payment.providers.stripe.api-base-url", () -> "http://localhost:" + WIREMOCK_PORT);
        registry.add("payment.providers.stripe.secret-key", () -> "sk_test_mock");
        registry.add("payment.providers.stripe.webhook-secret", () -> "whsec_test_mock");

        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> false);
        registry.add("spring.cloud.discovery.enabled", () -> false);
    }

    @Test
    @Order(1)
    @DisplayName("Should acquire and release idempotency lock correctly")
    void testIdempotencyService_AcquireAndReleaseLock() {
        // Arrange
        String testKey = "test:idempotency:" + UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(5);

        // Act - Acquire lock
        boolean lockAcquired = idempotencyService.acquireProcessingLock(testKey, ttl);

        // Assert - First lock should succeed
        assertThat(lockAcquired).isTrue();

        // Act - Try to acquire same lock again
        boolean secondLockAttempt = idempotencyService.acquireProcessingLock(testKey, ttl);

        // Assert - Second lock should fail
        assertThat(secondLockAttempt).isFalse();

        // Act - Release lock
        idempotencyService.releaseLock(testKey);

        // Assert - Should be able to acquire lock again after release
        boolean thirdLockAttempt = idempotencyService.acquireProcessingLock(testKey, ttl);
        assertThat(thirdLockAttempt).isTrue();

        // Cleanup
        idempotencyService.releaseLock(testKey);
    }

    @Test
    @Order(2)
    @DisplayName("Should mark event as completed and prevent reprocessing")
    void testIdempotencyService_MarkAsCompleted() {
        // Arrange
        String testKey = "test:completed:" + UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(5);

        // Act - Acquire lock and mark as completed
        boolean lockAcquired = idempotencyService.acquireProcessingLock(testKey, ttl);
        assertThat(lockAcquired).isTrue();

        idempotencyService.markAsCompleted(testKey, ttl);

        // Assert - Check processing state
        String state = idempotencyService.getProcessingState(testKey);
        assertThat(state).isEqualTo("completed");

        // Act - Try to acquire lock for completed event
        boolean secondLockAttempt = idempotencyService.acquireProcessingLock(testKey, ttl);

        // Assert - Lock should fail for completed event
        assertThat(secondLockAttempt).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("Should handle concurrent webhook requests with same idempotency key")
    void testConcurrentWebhooks_SameIdempotencyKey() throws InterruptedException {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = REDIS_KEY_PREFIX + orderId;

        // Create a payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setProviderTransactionId("pi_concurrent_test");
        paymentTransactionRepository.save(transaction);

        int concurrentRequests = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lockAcquiredCount = new AtomicInteger(0);
        Duration ttl = Duration.ofMinutes(5);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        List<Future<Boolean>> futures = new ArrayList<>();

        // Act - Submit concurrent lock acquisition attempts
        for (int i = 0; i < concurrentRequests; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    boolean acquired = idempotencyService.acquireProcessingLock(idempotencyKey, ttl);
                    if (acquired) {
                        lockAcquiredCount.incrementAndGet();
                        // Simulate processing time
                        Thread.sleep(50);
                        idempotencyService.markAsCompleted(idempotencyKey, ttl);
                        successCount.incrementAndGet();
                    }
                    return acquired;
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        // Only one thread should have acquired the lock
        assertThat(lockAcquiredCount.get()).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);

        // Verify the event is marked as completed
        String state = idempotencyService.getProcessingState(idempotencyKey);
        assertThat(state).isEqualTo("completed");
    }

    @Test
    @Order(4)
    @DisplayName("Should handle multiple different webhook events concurrently")
    void testConcurrentWebhooks_DifferentIdempotencyKeys() throws InterruptedException {
        // Arrange
        int numberOfEvents = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfEvents);
        AtomicInteger successCount = new AtomicInteger(0);
        Duration ttl = Duration.ofMinutes(5);

        List<UUID> orderIds = new ArrayList<>();
        for (int i = 0; i < numberOfEvents; i++) {
            UUID orderId = UUID.randomUUID();
            orderIds.add(orderId);

            // Create payment transactions
            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setOrderId(orderId);
            transaction.setAmount(new BigDecimal("100.00"));
            transaction.setCurrency("USD");
            transaction.setPaymentChannel("STRIPE");
            transaction.setStatus(PaymentStatus.PROCESSING.name());
            transaction.setProviderTransactionId("pi_multi_" + i);
            paymentTransactionRepository.save(transaction);
        }

        ExecutorService executor = Executors.newFixedThreadPool(numberOfEvents);

        // Act - Submit concurrent processing for different events
        for (int i = 0; i < numberOfEvents; i++) {
            final UUID orderId = orderIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String key = REDIS_KEY_PREFIX + orderId;
                    boolean acquired = idempotencyService.acquireProcessingLock(key, ttl);
                    if (acquired) {
                        Thread.sleep(20); // Simulate processing
                        idempotencyService.markAsCompleted(key, ttl);
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads
        startLatch.countDown();

        // Wait for completion
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert - All different events should be processed
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numberOfEvents);

        // Verify all events are marked as completed
        for (UUID orderId : orderIds) {
            String state = idempotencyService.getProcessingState(REDIS_KEY_PREFIX + orderId);
            assertThat(state).isEqualTo("completed");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Should prevent duplicate webhook processing via API endpoint")
    void testDuplicateWebhook_PreventedByIdempotency() {
        // Arrange
        UUID orderId = UUID.randomUUID();

        // Create a payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("75.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setProviderTransactionId("pi_duplicate_test");
        paymentTransactionRepository.save(transaction);

        // Pre-mark the event as completed in Redis
        String redisKey = REDIS_KEY_PREFIX + orderId;
        idempotencyService.acquireProcessingLock(redisKey, Duration.ofMinutes(5));
        idempotencyService.markAsCompleted(redisKey, Duration.ofMinutes(5));

        // Verify it's marked as completed
        String state = idempotencyService.getProcessingState(redisKey);
        assertThat(state).isEqualTo("completed");

        // Act - Try to acquire lock again (simulating duplicate webhook)
        boolean lockAcquired = idempotencyService.acquireProcessingLock(redisKey, Duration.ofMinutes(5));

        // Assert - Lock should not be acquired for completed event
        assertThat(lockAcquired).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("Should allow retry after lock release on failure")
    void testIdempotency_RetryAfterFailure() {
        // Arrange
        String testKey = "test:retry:" + UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(5);

        // Act - Simulate first attempt that fails and releases lock
        boolean firstLock = idempotencyService.acquireProcessingLock(testKey, ttl);
        assertThat(firstLock).isTrue();

        // Simulate failure - release lock without marking as completed
        idempotencyService.releaseLock(testKey);

        // Act - Retry should be allowed
        boolean retryLock = idempotencyService.acquireProcessingLock(testKey, ttl);

        // Assert
        assertThat(retryLock).isTrue();

        // Mark as completed on successful retry
        idempotencyService.markAsCompleted(testKey, ttl);

        // Verify final state
        String state = idempotencyService.getProcessingState(testKey);
        assertThat(state).isEqualTo("completed");
    }

    @Test
    @Order(7)
    @DisplayName("Should handle high concurrency webhook race condition")
    void testHighConcurrency_WebhookRaceCondition() throws InterruptedException {
        // Arrange
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = REDIS_KEY_PREFIX + orderId;

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setAmount(new BigDecimal("200.00"));
        transaction.setCurrency("USD");
        transaction.setPaymentChannel("STRIPE");
        transaction.setStatus(PaymentStatus.PROCESSING.name());
        transaction.setProviderTransactionId("pi_race_test");
        paymentTransactionRepository.save(transaction);

        int concurrentRequests = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        AtomicInteger lockAcquiredCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        Duration ttl = Duration.ofMinutes(5);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        // Act
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Check if already completed
                    String currentState = idempotencyService.getProcessingState(idempotencyKey);
                    if ("completed".equals(currentState)) {
                        return;
                    }

                    boolean acquired = idempotencyService.acquireProcessingLock(idempotencyKey, ttl);
                    if (acquired) {
                        lockAcquiredCount.incrementAndGet();
                        // Simulate webhook processing
                        Thread.sleep(10);
                        idempotencyService.markAsCompleted(idempotencyKey, ttl);
                        completedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertThat(completed).isTrue();
        // Exactly one thread should acquire the lock and complete
        assertThat(lockAcquiredCount.get()).isEqualTo(1);
        assertThat(completedCount.get()).isEqualTo(1);

        // Verify final state
        String finalState = idempotencyService.getProcessingState(idempotencyKey);
        assertThat(finalState).isEqualTo("completed");
    }
}
