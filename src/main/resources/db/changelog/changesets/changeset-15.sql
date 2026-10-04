-- liquibase formatted sql

-- changeset xdpsx:pricing-cr2-storefront-cart-order-snapshots
-- comment: Replace Product pricing projection with SKU-aware Cart identity and immutable OrderItem snapshots.

-- Legacy rows cannot identify the selected SKU or reconstruct historical money values. The approved CR2
-- policy is to clear them before enforcing the new non-null schema.
DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;

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

SET @order_item_product_fk = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_items'
      AND COLUMN_NAME = 'product_id'
      AND REFERENCED_TABLE_NAME = 'products'
    LIMIT 1
);
SET @drop_order_item_product_fk = IF(
    @order_item_product_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE order_items DROP FOREIGN KEY `', @order_item_product_fk, '`')
);
PREPARE drop_order_item_product_fk FROM @drop_order_item_product_fk;
EXECUTE drop_order_item_product_fk;
DEALLOCATE PREPARE drop_order_item_product_fk;
ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(255) NULL,
    ADD COLUMN variant_id BIGINT NULL,
    ADD COLUMN sku VARCHAR(128) NULL,
    ADD COLUMN variant_description VARCHAR(1000) NULL,
    ADD COLUMN unit_base_price DECIMAL(15,2) NULL,
    ADD COLUMN discount_amount DECIMAL(15,2) NULL,
    ADD COLUMN final_unit_price DECIMAL(15,2) NULL,
    ADD COLUMN subtotal DECIMAL(20,2) NULL,
    ADD COLUMN currency VARCHAR(3) NULL;

ALTER TABLE order_items
    MODIFY COLUMN product_id BIGINT NOT NULL,
    MODIFY COLUMN quantity INT NOT NULL,
    MODIFY COLUMN product_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN variant_id BIGINT NOT NULL,
    MODIFY COLUMN sku VARCHAR(128) NOT NULL,
    MODIFY COLUMN variant_description VARCHAR(1000) NOT NULL,
    MODIFY COLUMN unit_base_price DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN discount_amount DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN final_unit_price DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN subtotal DECIMAL(20,2) NOT NULL,
    MODIFY COLUMN currency VARCHAR(3) NOT NULL;

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_item_money_non_negative CHECK (unit_base_price >= 0 AND discount_amount >= 0
        AND final_unit_price >= 0 AND subtotal >= 0),
    ADD INDEX ix_order_item_order (order_id);

ALTER TABLE products DROP COLUMN price, DROP COLUMN discount_percent;
