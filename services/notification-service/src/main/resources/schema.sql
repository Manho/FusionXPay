CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    orderId CHAR(36) NOT NULL,
    eventType VARCHAR(50),
    content TEXT,
    recipient VARCHAR(255),
    status VARCHAR(50),
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_orderId (orderId),          
    INDEX idx_createdAt (createdAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;