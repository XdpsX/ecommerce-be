-- liquibase formatted sql

-- changeset ecommerce:media-foundation-baseline
-- comment: Squashed media-foundation schema for fresh development databases.

CREATE TABLE IF NOT EXISTS media (
    id VARCHAR(30) NOT NULL PRIMARY KEY, -- display_name in cloudinary
    external_id VARCHAR(50) NOT NULL UNIQUE, -- public_id in cloudinary
    url VARCHAR(255) NOT NULL,
    caption VARCHAR(100),
    content_type VARCHAR(30) NOT NULL,
    resource_type TINYINT(1) NOT NULL,
    temp_flg BOOLEAN DEFAULT FALSE,
    delete_flg BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE categories
DROP COLUMN slug;

ALTER TABLE categories
MODIFY COLUMN created_at TIMESTAMP NOT NULL,
MODIFY COLUMN updated_at TIMESTAMP;

ALTER TABLE categories
ADD COLUMN public_flg BOOLEAN NOT NULL DEFAULT FALSE AFTER name,
ADD COLUMN image_id VARCHAR(30) AFTER public_flg,
ADD COLUMN parent_id INT AFTER image_id,
ADD CONSTRAINT fk_category_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE SET NULL,
ADD CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL;

ALTER TABLE brands
DROP COLUMN logo;

ALTER TABLE brands
MODIFY COLUMN created_at TIMESTAMP NOT NULL,
MODIFY COLUMN updated_at TIMESTAMP;

ALTER TABLE brands
ADD COLUMN public_flg BOOLEAN NOT NULL DEFAULT FALSE AFTER name,
ADD COLUMN image_id VARCHAR(30) AFTER public_flg,
ADD CONSTRAINT fk_brand_media FOREIGN KEY (image_id) REFERENCES media(id) ON DELETE SET NULL;

ALTER TABLE media
ADD COLUMN purpose VARCHAR(32) NULL AFTER content_type,
ADD COLUMN status VARCHAR(32) NULL AFTER purpose;

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


