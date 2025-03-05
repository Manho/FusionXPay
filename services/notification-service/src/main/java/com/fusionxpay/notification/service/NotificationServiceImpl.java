package com.fusionxpay.notification.service;

import com.fusionxpay.notification.model.NotificationMessage;
import com.fusionxpay.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void createNotification(NotificationMessage notificationMessage) {
        notificationRepository.save(notificationMessage);
    }

    @Override
    public List<NotificationMessage> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @Override
    public Optional<NotificationMessage> getNotificationById(Long id) {
        return notificationRepository.findById(id);
    }

    @Override
    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }
}
