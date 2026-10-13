-- liquibase formatted sql

-- changeset xdpsx:media-async-processing
-- comment: Separate media attachment lifecycle from asynchronous eager processing state.
ALTER TABLE media
    CHANGE COLUMN status attachment_status VARCHAR(32) NOT NULL;

ALTER TABLE media
    ADD COLUMN processing_status VARCHAR(32) NULL AFTER attachment_status,
    ADD COLUMN processing_failure_reason VARCHAR(500) NULL AFTER processing_status,
    ADD COLUMN processing_reference VARCHAR(255) NULL AFTER processing_failure_reason;

UPDATE media
SET processing_status = 'READY'
WHERE processing_status IS NULL;

ALTER TABLE media
    MODIFY COLUMN processing_status VARCHAR(32) NOT NULL;

ALTER TABLE media
    ADD CONSTRAINT uq_media_processing_reference UNIQUE (processing_reference);
