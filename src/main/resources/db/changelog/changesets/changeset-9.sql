-- liquibase formatted sql

-- changeset xdpsx:product-cr3-media-integration
-- comment: Replace Product-owned image URLs with ordered Media attachments and protect ordered history.

DELETE FROM product_images;

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
