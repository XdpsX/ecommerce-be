-- liquibase formatted sql

-- changeset ecommerce:cart-baseline
-- comment: Squashed cart schema for fresh development databases.

ALTER TABLE cart_items
    DROP FOREIGN KEY cart_items_ibfk_1,
    DROP FOREIGN KEY cart_items_ibfk_2,
    DROP PRIMARY KEY;
ALTER TABLE cart_items DROP COLUMN product_id;
ALTER TABLE cart_items ADD COLUMN variant_id BIGINT NOT NULL;
ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_cart_item_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    ADD PRIMARY KEY (user_id, variant_id),
    ADD INDEX ix_cart_item_variant (variant_id);

CREATE TABLE carts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    guest_secret_hash BINARY(32) NULL,
    guest_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_user_id UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_carts_owner CHECK (
        (user_id IS NOT NULL AND guest_secret_hash IS NULL AND guest_expires_at IS NULL)
        OR (user_id IS NULL AND guest_secret_hash IS NOT NULL AND guest_expires_at IS NOT NULL)
    ),
    INDEX ix_carts_guest_expiry (guest_expires_at)
);

ALTER TABLE cart_items ADD COLUMN cart_id BIGINT NULL;

ALTER TABLE cart_items
    DROP FOREIGN KEY fk_cart_item_user,
    DROP FOREIGN KEY fk_cart_item_variant,
    DROP PRIMARY KEY;

ALTER TABLE cart_items DROP COLUMN user_id;

ALTER TABLE cart_items
    MODIFY COLUMN cart_id BIGINT NOT NULL,
    ADD CONSTRAINT pk_cart_items PRIMARY KEY (cart_id, variant_id),
    ADD CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_cart_item_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99);


