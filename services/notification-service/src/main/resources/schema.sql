
CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT,
    recipient VARCHAR(255),
    status VARCHAR(50),
    timestamp DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
