package com.fusionxpay.notification.service;

import com.fusionxpay.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupService {

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30); 
        log.info("Starting cleanup of notifications older than {}", threshold);

        int deletedCount = notificationRepository.deleteByCreatedAtBefore(threshold);
        log.info("Deleted {} old notifications", deletedCount);
    }
}