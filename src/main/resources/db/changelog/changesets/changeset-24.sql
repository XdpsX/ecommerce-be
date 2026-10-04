-- liquibase formatted sql

-- changeset xdpsx:fulfillment-cancellation-refund-slice-1
-- comment: Add Order cancellation metadata, cancellable payment states, and one full Refund per Payment.

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
