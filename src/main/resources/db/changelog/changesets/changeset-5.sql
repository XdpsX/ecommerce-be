-- liquibase formatted sql

-- changeset xdpsx:media-cr1-lifecycle
-- comment: Replace media resource_type/temp_flg/delete_flg with purpose/status and widen media IDs to UUID strings
-- Purpose is backfilled from the old ordinal resource_type. The mapping below is written explicitly
-- (0=CATEGORY, 1=BRAND, 2=PRODUCT, matching the historical enum order) instead of casting the ordinal,
-- so a future enum reorder cannot silently corrupt existing rows.
ALTER TABLE media
ADD COLUMN purpose VARCHAR(32) NULL AFTER content_type,
ADD COLUMN status VARCHAR(32) NULL AFTER purpose;

UPDATE media
SET purpose = CASE resource_type
    WHEN 0 THEN 'CATEGORY_IMAGE'
    WHEN 1 THEN 'BRAND_LOGO'
    WHEN 2 THEN 'PRODUCT_IMAGE'
END;

-- Deletion intent takes precedence over the temporary flag.
UPDATE media
SET status = CASE
    WHEN delete_flg = TRUE THEN 'PENDING_DELETE'
    WHEN temp_flg = TRUE THEN 'TEMPORARY'
    ELSE 'ACTIVE'
END;

ALTER TABLE media
MODIFY COLUMN purpose VARCHAR(32) NOT NULL,
MODIFY COLUMN status VARCHAR(32) NOT NULL;

ALTER TABLE media
DROP COLUMN resource_type,
DROP COLUMN temp_flg,
DROP COLUMN delete_flg;

-- MySQL refuses to alter a column that a foreign key references, so the two Media FKs are dropped
-- and recreated with the same names and ON DELETE SET NULL semantics.
ALTER TABLE categories DROP FOREIGN KEY fk_category_media;

ALTER TABLE brands DROP FOREIGN KEY fk_brand_media;

ALTER TABLE media
MODIFY COLUMN id VARCHAR(36) NOT NULL,
MODIFY COLUMN external_id VARCHAR(255) NOT NULL;

ALTER TABLE categories MODIFY COLUMN image_id VARCHAR(36);

ALTER TABLE brands MODIFY COLUMN image_id VARCHAR(36);

ALTER TABLE categories
ADD CONSTRAINT fk_category_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE SET NULL;

ALTER TABLE brands
ADD CONSTRAINT fk_brand_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE SET NULL;