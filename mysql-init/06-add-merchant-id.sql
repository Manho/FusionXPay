ALTER TABLE payment_transactions ADD COLUMN merchant_id BIGINT NULL;

DELETE pt
FROM payment_transactions pt
LEFT JOIN orders o ON pt.order_id = o.order_id
WHERE o.order_id IS NULL;

UPDATE payment_transactions pt
JOIN orders o ON pt.order_id = o.order_id
SET pt.merchant_id = o.user_id
WHERE pt.merchant_id IS NULL;

ALTER TABLE payment_transactions MODIFY COLUMN merchant_id BIGINT NOT NULL;

CREATE INDEX idx_payment_transactions_merchant_id ON payment_transactions (merchant_id);
