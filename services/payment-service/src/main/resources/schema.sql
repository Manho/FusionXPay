-- Create the payment_transactions table based on the PaymentTransaction entity
CREATE TABLE IF NOT EXISTS payment_transactions (
    transactionId CHAR(36) NOT NULL DEFAULT (UUID()),
    orderId CHAR(36) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    paymentChannel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    providerTransactionId VARCHAR(255),
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME NOT NULL,
    PRIMARY KEY (transactionId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
