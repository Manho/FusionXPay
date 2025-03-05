package com.fusionxpay.notification.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fusionxpay.notification.model.NotificationMessage;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationMessage, Long> {
    int deleteByCreatedAtBefore(LocalDateTime threshold);
}