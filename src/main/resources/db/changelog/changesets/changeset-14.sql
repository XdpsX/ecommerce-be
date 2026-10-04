-- liquibase formatted sql

-- changeset xdpsx:pricing-cr1-variant-base-price
-- comment: Move mutable base-price ownership to Product Variant while retaining Product compatibility projection.

ALTER TABLE product_variants
    ADD COLUMN base_price DECIMAL(15, 2) NULL;

UPDATE product_variants variant
JOIN products product ON product.id = variant.product_id
SET variant.base_price = product.price;

UPDATE products product
LEFT JOIN (
    SELECT variant.product_id, MIN(variant.base_price) AS minimum_active_base_price
    FROM product_variants variant
    WHERE variant.status = 'ACTIVE'
    GROUP BY variant.product_id
) active_variant ON active_variant.product_id = product.id
SET product.price = COALESCE(active_variant.minimum_active_base_price, 0.00);

ALTER TABLE product_variants
    MODIFY COLUMN base_price DECIMAL(15, 2) NOT NULL;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variant_base_price_range
        CHECK (base_price >= 0.00 AND base_price <= 1000000000.00);

ALTER TABLE products
    MODIFY COLUMN price DECIMAL(15, 2) NOT NULL DEFAULT 0.00;
