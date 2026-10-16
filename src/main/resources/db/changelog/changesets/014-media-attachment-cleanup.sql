-- liquibase formatted sql

-- changeset ecommerce:media-attachment-owner-and-cleanup
-- comment: Adds immutable attachment ownership and retry scheduling without backfilling legacy Media.
ALTER TABLE media
    ADD COLUMN attachment_owner_type VARCHAR(32) NULL,
    ADD COLUMN attachment_owner_id VARCHAR(64) NULL,
    ADD COLUMN cleanup_next_attempt_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT ck_media_attachment_owner_pair
        CHECK ((attachment_owner_type IS NULL AND attachment_owner_id IS NULL)
            OR (attachment_owner_type IS NOT NULL AND attachment_owner_id IS NOT NULL
                AND TRIM(attachment_owner_id) <> '')),
    ADD CONSTRAINT ck_media_attachment_owner_type
        CHECK (attachment_owner_type IS NULL OR attachment_owner_type IN ('CATEGORY', 'BRAND', 'PRODUCT')),
    ADD INDEX ix_media_attachment_owner (attachment_owner_type, attachment_owner_id, purpose),
    ADD INDEX ix_media_cleanup_due (attachment_status, cleanup_next_attempt_at, id);

-- changeset ecommerce:category-media-delete-restrict
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS rc JOIN information_schema.KEY_COLUMN_USAGE kcu ON kcu.CONSTRAINT_SCHEMA=rc.CONSTRAINT_SCHEMA AND kcu.TABLE_NAME=rc.TABLE_NAME AND kcu.CONSTRAINT_NAME=rc.CONSTRAINT_NAME WHERE rc.CONSTRAINT_SCHEMA=DATABASE() AND rc.TABLE_NAME='categories' AND rc.CONSTRAINT_NAME='fk_category_media' AND rc.DELETE_RULE='SET NULL' AND kcu.COLUMN_NAME='image_id' AND kcu.REFERENCED_TABLE_NAME='media' AND kcu.REFERENCED_COLUMN_NAME='id'
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM categories c LEFT JOIN media m ON m.id=c.image_id WHERE c.image_id IS NOT NULL AND m.id IS NULL
ALTER TABLE categories DROP FOREIGN KEY fk_category_media;

ALTER TABLE categories
    ADD CONSTRAINT fk_category_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- changeset ecommerce:brand-media-delete-restrict
-- preconditions onFail:HALT onError:HALT
-- precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS rc JOIN information_schema.KEY_COLUMN_USAGE kcu ON kcu.CONSTRAINT_SCHEMA=rc.CONSTRAINT_SCHEMA AND kcu.TABLE_NAME=rc.TABLE_NAME AND kcu.CONSTRAINT_NAME=rc.CONSTRAINT_NAME WHERE rc.CONSTRAINT_SCHEMA=DATABASE() AND rc.TABLE_NAME='brands' AND rc.CONSTRAINT_NAME='fk_brand_media' AND rc.DELETE_RULE='SET NULL' AND kcu.COLUMN_NAME='image_id' AND kcu.REFERENCED_TABLE_NAME='media' AND kcu.REFERENCED_COLUMN_NAME='id'
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM brands b LEFT JOIN media m ON m.id=b.image_id WHERE b.image_id IS NOT NULL AND m.id IS NULL
ALTER TABLE brands DROP FOREIGN KEY fk_brand_media;

ALTER TABLE brands
    ADD CONSTRAINT fk_brand_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
