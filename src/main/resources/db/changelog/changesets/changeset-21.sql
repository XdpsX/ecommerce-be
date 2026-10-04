-- liquibase formatted sql

-- changeset xdpsx:checkout-renewal-atomic-checkout
-- comment: Add idempotent Checkout fields, structured shipping snapshots, and reservation expiry.

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

UPDATE orders SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';

UPDATE orders o
LEFT JOIN users u ON u.id = o.user_id
SET o.recipient_name = COALESCE(u.name, 'Legacy customer'),
    o.phone_number = o.mobile_number,
    o.address_line = o.address,
    o.currency = 'VND'
WHERE o.currency IS NULL;

ALTER TABLE orders
    MODIFY COLUMN currency VARCHAR(3) NOT NULL,
    ADD CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key_hash),
    ADD INDEX ix_orders_reservation_expiry (status, reservation_expires_at);
