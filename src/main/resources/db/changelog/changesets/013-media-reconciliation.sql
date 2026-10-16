-- liquibase formatted sql

-- changeset ecommerce:media-reconciliation
-- comment: Persists bounded reconciliation scheduling state for media uploads and processing.

ALTER TABLE media
    ADD COLUMN reconciliation_next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN processing_reconciliation_attempts INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_media_processing_reconciliation_attempts_nonnegative
        CHECK (processing_reconciliation_attempts >= 0),
    ADD INDEX ix_media_pending_reconciliation
        (upload_status, attachment_status, upload_authorization_expires_at, reconciliation_next_attempt_at, id),
    ADD INDEX ix_media_processing_reconciliation
        (upload_status, processing_status, attachment_status, reconciliation_next_attempt_at, id);
