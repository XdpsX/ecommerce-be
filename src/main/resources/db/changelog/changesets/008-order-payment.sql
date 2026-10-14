-- liquibase formatted sql

-- changeset ecommerce:order-payment-baseline
-- comment: Squashed order-payment schema for fresh development databases.

ALTER TABLE order_items DROP FOREIGN KEY fk_order_item_product;

ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(255) NULL,
    ADD COLUMN variant_id BIGINT NULL,
    ADD COLUMN sku VARCHAR(128) NULL,
    ADD COLUMN variant_description VARCHAR(1000) NULL,
    ADD COLUMN unit_base_price DECIMAL(15,2) NULL,
    ADD COLUMN discount_amount DECIMAL(15,2) NULL,
    ADD COLUMN final_unit_price DECIMAL(15,2) NULL,
    ADD COLUMN subtotal DECIMAL(20,2) NULL,
    ADD COLUMN currency VARCHAR(3) NULL;

ALTER TABLE order_items
    MODIFY COLUMN product_id BIGINT NOT NULL,
    MODIFY COLUMN quantity INT NOT NULL,
    MODIFY COLUMN product_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN variant_id BIGINT NOT NULL,
    MODIFY COLUMN sku VARCHAR(128) NOT NULL,
    MODIFY COLUMN variant_description VARCHAR(1000) NOT NULL,
    MODIFY COLUMN unit_base_price DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN discount_amount DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN final_unit_price DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN subtotal DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_item_money_non_negative CHECK (unit_base_price >= 0 AND discount_amount >= 0
        AND final_unit_price >= 0 AND subtotal >= 0),
    ADD INDEX ix_order_item_order (order_id);

ALTER TABLE products DROP COLUMN price, DROP COLUMN discount_percent;

ALTER TABLE orders
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD COLUMN recipient_name VARCHAR(128) NULL,
    ADD COLUMN phone_number VARCHAR(20) NULL,
    ADD COLUMN address_line VARCHAR(255) NULL,
    ADD COLUMN ward_commune VARCHAR(128) NULL,
    ADD COLUMN district VARCHAR(128) NULL,
    ADD COLUMN province_city VARCHAR(128) NULL,
    ADD COLUMN postal_code VARCHAR(20) NULL,
    ADD COLUMN currency VARCHAR(3) NULL,
    ADD COLUMN idempotency_key_hash BINARY(32) NULL,
    ADD COLUMN checkout_request_hash BINARY(32) NULL,
    ADD COLUMN reservation_expires_at DATETIME(6) NULL;

ALTER TABLE orders
    MODIFY COLUMN currency VARCHAR(3) NOT NULL,
    ADD CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key_hash),
    ADD INDEX ix_orders_reservation_expiry (status, reservation_expires_at);

ALTER TABLE payments MODIFY COLUMN status VARCHAR(32) NOT NULL;

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

ALTER TABLE orders
    ADD COLUMN cancellation_reason VARCHAR(500) NULL,
    ADD COLUMN cancelled_by VARCHAR(320) NULL,
    ADD COLUMN cancelled_at DATETIME(6) NULL;

ALTER TABLE payment_attempts
    DROP CHECK ck_payment_attempts_status,
    ADD CONSTRAINT ck_payment_attempts_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED'));

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by VARCHAR(320) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    processed_by VARCHAR(320) NULL,
    completed_at DATETIME(6) NULL,
    external_reference VARCHAR(100) NULL,
    failure_reason VARCHAR(500) NULL,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT uk_refunds_payment_id UNIQUE (payment_id),
    CONSTRAINT uk_refunds_external_reference UNIQUE (external_reference),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_currency CHECK (currency = 'VND'),
    INDEX ix_refunds_status_requested_at (status, requested_at)
);


