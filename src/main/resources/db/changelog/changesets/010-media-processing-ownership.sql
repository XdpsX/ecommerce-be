-- liquibase formatted sql

-- changeset ecommerce:media-processing-ownership-baseline
-- comment: Squashed media-processing-ownership schema for fresh development databases.

ALTER TABLE media
    CHANGE COLUMN status attachment_status VARCHAR(32) NOT NULL;

ALTER TABLE media
    ADD COLUMN processing_status VARCHAR(32) NULL AFTER attachment_status,
    ADD COLUMN processing_failure_reason VARCHAR(500) NULL AFTER processing_status,
    ADD COLUMN processing_reference VARCHAR(255) NULL AFTER processing_failure_reason;

ALTER TABLE media
    MODIFY COLUMN processing_status VARCHAR(32) NOT NULL;

ALTER TABLE media
    ADD CONSTRAINT uq_media_processing_reference UNIQUE (processing_reference);

ALTER TABLE media
    ADD COLUMN uploaded_by_user_id BIGINT NULL AFTER purpose;


