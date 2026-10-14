-- liquibase formatted sql

-- changeset ecommerce:catalog-model-baseline
-- comment: Squashed catalog-model schema for fresh development databases.

ALTER TABLE categories
ADD COLUMN status VARCHAR(16) NULL AFTER name,
ADD COLUMN slug VARCHAR(160) NULL AFTER status,
ADD COLUMN display_order INT NULL AFTER slug;

ALTER TABLE categories
MODIFY COLUMN status VARCHAR(16) NOT NULL,
MODIFY COLUMN slug VARCHAR(160) NOT NULL,
MODIFY COLUMN display_order INT NOT NULL;

ALTER TABLE categories
ADD CONSTRAINT uk_category_slug UNIQUE (slug),
ADD INDEX idx_category_parent_order (parent_id, display_order);

-- MySQL refuses to change a foreign key action in place, so the constraint is dropped and recreated
-- with the same name. Children must never be silently promoted to roots.
ALTER TABLE categories DROP FOREIGN KEY fk_parent_category;

ALTER TABLE categories
ADD CONSTRAINT fk_parent_category FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE RESTRICT;

ALTER TABLE categories DROP COLUMN public_flg;

-- A sibling group cannot always be locked through its own rows: the root group is addressed by parent_id IS NULL
-- and an empty group has no row at all. Locking this one shared row (SELECT ... FOR UPDATE) gives every hierarchy
-- writer something to contend on, and the row lock is released by commit or rollback.

CREATE TABLE category_hierarchy_anchor (
    id INT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO category_hierarchy_anchor (id) VALUES (1);

ALTER TABLE brands
ADD COLUMN status VARCHAR(16) NULL AFTER name,
ADD COLUMN version BIGINT NULL DEFAULT 0 AFTER status;

ALTER TABLE brands
MODIFY COLUMN status VARCHAR(16) NOT NULL,
MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE brands
DROP COLUMN public_flg;

ALTER TABLE products DROP COLUMN main_image;

ALTER TABLE product_images DROP FOREIGN KEY product_images_ibfk_1;
ALTER TABLE product_images DROP COLUMN url;
ALTER TABLE product_images
    ADD COLUMN media_id VARCHAR(36) NOT NULL,
    ADD COLUMN display_order INT NOT NULL;
ALTER TABLE product_images MODIFY COLUMN product_id BIGINT NOT NULL;

ALTER TABLE product_images
    ADD CONSTRAINT fk_product_image_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_product_image_media UNIQUE (media_id),
    ADD CONSTRAINT uk_product_image_order UNIQUE (product_id, display_order),
    ADD CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

ALTER TABLE order_items DROP FOREIGN KEY order_items_ibfk_2;
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

ALTER TABLE categories
    ADD COLUMN version BIGINT NULL DEFAULT 0;

ALTER TABLE categories
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;



