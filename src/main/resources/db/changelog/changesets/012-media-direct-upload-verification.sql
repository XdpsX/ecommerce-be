-- liquibase formatted sql

-- changeset ecommerce:media-direct-upload-verification
-- comment: Stores Cloudinary signed-upload authorization and authoritative provider verification metadata.

ALTER TABLE media
    ADD COLUMN asset_id VARCHAR(255) NULL,
    ADD COLUMN upload_authorization_issued_at TIMESTAMP(6) NULL,
    ADD COLUMN upload_authorization_expires_at TIMESTAMP(6) NULL,
    ADD COLUMN uploaded_at TIMESTAMP(6) NULL,
    ADD COLUMN bytes BIGINT NULL,
    ADD COLUMN width INT NULL,
    ADD COLUMN height INT NULL,
    ADD COLUMN format VARCHAR(32) NULL,
    ADD COLUMN upload_failure_reason VARCHAR(500) NULL,
    ADD CONSTRAINT uq_media_asset_id UNIQUE (asset_id),
    ADD CONSTRAINT ck_media_upload_dimensions_positive CHECK (
        (bytes IS NULL OR bytes > 0)
        AND (width IS NULL OR width > 0)
        AND (height IS NULL OR height > 0)
    ),
    ADD CONSTRAINT ck_media_signed_authorization_window CHECK (
        (upload_authorization_issued_at IS NULL AND upload_authorization_expires_at IS NULL)
        OR (upload_authorization_issued_at IS NOT NULL
            AND upload_authorization_expires_at IS NOT NULL
            AND upload_authorization_issued_at < upload_authorization_expires_at)
    ),
    ADD CONSTRAINT ck_media_pending_signed_authorization CHECK (
        upload_status <> 'PENDING_UPLOAD'
        OR (upload_authorization_issued_at IS NOT NULL AND upload_authorization_expires_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_media_signed_uploaded_metadata CHECK (
        upload_authorization_issued_at IS NULL
        OR upload_status <> 'UPLOADED'
        OR (asset_id IS NOT NULL AND uploaded_at IS NOT NULL
            AND bytes IS NOT NULL AND bytes > 0
            AND width IS NOT NULL AND width > 0
            AND height IS NOT NULL AND height > 0
            AND format IS NOT NULL AND format <> '')
    );
