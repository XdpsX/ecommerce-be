-- liquibase formatted sql

-- changeset xdpsx:payment-attempt-renewal
-- comment: Add payment attempt identity and migrate the Payment summary lifecycle.
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM (SELECT order_id FROM payments WHERE order_id IS NOT NULL GROUP BY order_id HAVING COUNT(*) > 1) duplicate_orders

ALTER TABLE payments MODIFY COLUMN status VARCHAR(32) NOT NULL;

UPDATE payments p
JOIN orders o ON o.id = p.order_id
SET p.status = 'EXPIRED'
WHERE p.status = 'UNPAID'
  AND o.status = 'PAYMENT_EXPIRED';

UPDATE payments
SET status = 'PENDING'
WHERE status = 'UNPAID';

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_id UNIQUE (order_id);

CREATE TABLE payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    provider_reference VARCHAR(100) NOT NULL,
    provider_transaction_id VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL,
    expected_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    response_code VARCHAR(16) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT uk_payment_attempts_provider_reference UNIQUE (provider_reference),
    CONSTRAINT uk_payment_attempts_provider_transaction_id UNIQUE (provider_transaction_id),
    CONSTRAINT ck_payment_attempts_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_payment_attempts_amount CHECK (expected_amount > 0),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency = 'VND'),
    INDEX ix_payment_attempts_payment_status_expiry (payment_id, status, expires_at)
);
