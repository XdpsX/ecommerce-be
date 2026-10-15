-- liquibase formatted sql

-- changeset ecommerce:media-upload-lifecycle
-- comment: Adds upload-intent lifecycle fields for the fresh development baseline.

ALTER TABLE media
    ADD COLUMN upload_status VARCHAR(32) NOT NULL AFTER uploaded_by_user_id,
    ADD COLUMN intent_expires_at TIMESTAMP(6) NULL AFTER upload_status,
    ADD COLUMN expected_content_type VARCHAR(100) NULL AFTER intent_expires_at,
    ADD COLUMN expected_size_bytes BIGINT NULL AFTER expected_content_type;

ALTER TABLE media
    MODIFY COLUMN url VARCHAR(255) NULL,
    MODIFY COLUMN content_type VARCHAR(30) NULL,
    ADD CONSTRAINT ck_media_expected_size_positive
        CHECK (expected_size_bytes IS NULL OR expected_size_bytes > 0),
    ADD CONSTRAINT ck_media_pending_upload_metadata
        CHECK (upload_status <> 'PENDING_UPLOAD' OR (
            intent_expires_at IS NOT NULL
            AND expected_content_type IS NOT NULL
            AND expected_size_bytes IS NOT NULL
        )),
    ADD CONSTRAINT ck_media_pending_not_active
        CHECK (upload_status <> 'PENDING_UPLOAD' OR attachment_status <> 'ACTIVE'),
    ADD CONSTRAINT ck_media_uploaded_not_pending_processing
        CHECK (upload_status <> 'UPLOADED' OR processing_status <> 'PENDING'),
    ADD CONSTRAINT ck_media_uploaded_asset_metadata
        CHECK (upload_status <> 'UPLOADED' OR (url IS NOT NULL AND content_type IS NOT NULL));
