-- Merchant table for admin-service
-- FusionXPay Admin Management

CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_code VARCHAR(32) UNIQUE NOT NULL COMMENT 'Unique merchant code',
    merchant_name VARCHAR(100) NOT NULL COMMENT 'Merchant display name',
    email VARCHAR(100) UNIQUE NOT NULL COMMENT 'Login email',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    role ENUM('ADMIN', 'MERCHANT') DEFAULT 'MERCHANT' COMMENT 'User role',
    status ENUM('ACTIVE', 'DISABLED') DEFAULT 'ACTIVE' COMMENT 'Account status',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_email (email),
    INDEX idx_merchant_code (merchant_code),
    INDEX idx_merchant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert default admin user (password: admin123)
INSERT INTO merchant (merchant_code, merchant_name, email, password_hash, role, status)
VALUES (
    'ADMIN001',
    'System Administrator',
    'admin@fusionxpay.com',
    '$2a$10$t8dRRDnNK/P2Ybqc1GWEZOZrwTta50Yj1gnGGDxOYNTzS27d9jZ2K',
    'ADMIN',
    'ACTIVE'
) ON DUPLICATE KEY UPDATE password_hash = '$2a$10$t8dRRDnNK/P2Ybqc1GWEZOZrwTta50Yj1gnGGDxOYNTzS27d9jZ2K';

-- Insert test merchant (password: merchant123)
INSERT INTO merchant (merchant_code, merchant_name, email, password_hash, role, status)
VALUES (
    'MCH001',
    'Test Merchant',
    'merchant@example.com',
    '$2a$10$9XBNrjIMFthosuvQzFyxoeB9HLbcbskvU2SqlrAgfD4TKz.mA7ZN.',
    'MERCHANT',
    'ACTIVE'
) ON DUPLICATE KEY UPDATE password_hash = '$2a$10$9XBNrjIMFthosuvQzFyxoeB9HLbcbskvU2SqlrAgfD4TKz.mA7ZN.';
