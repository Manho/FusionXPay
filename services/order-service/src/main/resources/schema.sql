CREATE TABLE IF NOT EXISTS orders (
    orderId CHAR(36) NOT NULL,
    orderNumber VARCHAR(255) NOT NULL,
    userId BIGINT,
    amount DECIMAL(19, 2),
    currency VARCHAR(50),
    status VARCHAR(50),
    createdAt DATETIME DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (orderId),
    UNIQUE KEY unique_order_number (orderNumber)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;