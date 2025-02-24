package com.fusionxpay.notification.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationMessage {
    private Long id;
    private String content;
    private String recipient;
    private String status;
    private LocalDateTime timestamp;
}
