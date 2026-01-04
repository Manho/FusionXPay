package com.fusionxpay.notification.service;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("Create notification persists message")
    void testCreateNotification() {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Test notification")
                .recipient("user@example.com")
                .createdAt(LocalDateTime.now())
                .build();

        notificationService.createNotification(message);

        List<NotificationMessage> saved = notificationRepository.findAll();
        assertEquals(1, saved.size());
        assertEquals("Test notification", saved.get(0).getContent());
    }

    @Test
    @DisplayName("Get all notifications returns stored messages")
    void testGetAllNotifications() {
        NotificationMessage message1 = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Notification 1")
                .recipient("user1@example.com")
                .build();

        NotificationMessage message2 = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Notification 2")
                .recipient("user2@example.com")
                .build();

        notificationRepository.save(message1);
        notificationRepository.save(message2);

        List<NotificationMessage> actualNotifications = notificationService.getAllNotifications();

        assertEquals(2, actualNotifications.size());
    }

    @Test
    @DisplayName("Get notification by ID returns message")
    void testGetNotificationById_WhenExists() {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Test notification")
                .recipient("user@example.com")
                .build();

        NotificationMessage saved = notificationRepository.save(message);

        Optional<NotificationMessage> result = notificationService.getNotificationById(saved.getId());

        assertTrue(result.isPresent());
        assertEquals(saved.getId(), result.get().getId());
    }

    @Test
    @DisplayName("Get notification by ID returns empty when missing")
    void testGetNotificationById_WhenNotExists() {
        Optional<NotificationMessage> result = notificationService.getNotificationById(999L);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Delete notification removes record")
    void testDeleteNotification() {
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Delete me")
                .recipient("user@example.com")
                .build();

        NotificationMessage saved = notificationRepository.save(message);
        notificationService.deleteNotification(saved.getId());

        assertFalse(notificationRepository.findById(saved.getId()).isPresent());
    }
}
