package com.fusionxpay.notification.service;

import com.fusionxpay.notification.model.NotificationMessage;

import java.util.List;
import java.util.Optional;

public interface NotificationService {
    void createNotification(NotificationMessage notificationMessage);
    List<NotificationMessage> getAllNotifications();
    Optional<NotificationMessage> getNotificationById(Long id);
    void deleteNotification(Long id);
}