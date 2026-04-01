CREATE TABLE IF NOT EXISTS ai_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    source VARCHAR(8) NOT NULL,
    merchant_id BIGINT NULL,
    action_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT,
    input_summary TEXT,
    output_summary TEXT,
    conversation_id VARCHAR(36),
    correlation_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_merchant (merchant_id),
    INDEX idx_audit_action (action_name),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
