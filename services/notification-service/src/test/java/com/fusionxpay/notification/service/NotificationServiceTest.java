package com.fusionxpay.notification.service;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.repository.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    // Assume we have a concrete implementation called NotificationServiceImpl
    @InjectMocks
    private NotificationServiceImpl notificationService;

    // Mock any dependencies the implementation might have
    @Mock
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateNotification() {
        // Arrange
        NotificationMessage message = NotificationMessage.builder()
                .orderId(UUID.randomUUID().toString())
                .content("Test notification")
                .createdAt(LocalDateTime.now())
                .build();

        // Act
        notificationService.createNotification(message);

        // Assert
        verify(notificationRepository, times(1)).save(message);
    }

    @Test
    void testGetAllNotifications() {
        // Arrange
        NotificationMessage message1 = NotificationMessage.builder()
                .id(1L)
                .content("Notification 1")
                .build();

        NotificationMessage message2 = NotificationMessage.builder()
                .id(2L)
                .content("Notification 2")
                .build();

        List<NotificationMessage> expectedNotifications = Arrays.asList(message1, message2);
        when(notificationRepository.findAll()).thenReturn(expectedNotifications);

        // Act
        List<NotificationMessage> actualNotifications = notificationService.getAllNotifications();

        // Assert
        assertEquals(expectedNotifications, actualNotifications);
        verify(notificationRepository, times(1)).findAll();
    }

    @Test
    void testGetNotificationById_WhenExists() {
        // Arrange
        Long id = 1L;
        NotificationMessage message = NotificationMessage.builder()
                .id(id)
                .content("Test notification")
                .build();

        when(notificationRepository.findById(id)).thenReturn(Optional.of(message));

        // Act
        Optional<NotificationMessage> result = notificationService.getNotificationById(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(message, result.get());
        verify(notificationRepository, times(1)).findById(id);
    }

    @Test
    void testGetNotificationById_WhenNotExists() {
        // Arrange
        Long id = 99L;
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        Optional<NotificationMessage> result = notificationService.getNotificationById(id);

        // Assert
        assertFalse(result.isPresent());
        verify(notificationRepository, times(1)).findById(id);
    }

    @Test
    void testDeleteNotification() {
        // Arrange
        Long id = 1L;

        // Act
        notificationService.deleteNotification(id);

        // Assert
        verify(notificationRepository, times(1)).deleteById(id);
    }
}
