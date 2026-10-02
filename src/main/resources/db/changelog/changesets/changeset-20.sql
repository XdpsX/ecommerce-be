-- liquibase formatted sql

-- changeset xdpsx:cart-renewal-foundation
-- comment: Replace user-owned Cart items with an owner-capable Cart aggregate while preserving legacy rows.

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

INSERT INTO carts (user_id, created_at)
SELECT DISTINCT user_id, CURRENT_TIMESTAMP(6)
FROM cart_items;

ALTER TABLE cart_items ADD COLUMN cart_id BIGINT NULL;

UPDATE cart_items ci
JOIN carts c ON c.user_id = ci.user_id
SET ci.cart_id = c.id;

-- Legacy Cart accepted any positive quantity. Normalize legacy rows before enforcing the renewed 1..99 invariant.
UPDATE cart_items
SET quantity = CASE
    WHEN quantity < 1 THEN 1
    WHEN quantity > 99 THEN 99
    ELSE quantity
END
WHERE quantity < 1 OR quantity > 99;

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
