-- liquibase formatted sql

-- changeset xdpsx:pricing-cr3-scheduled-variant-sale
-- comment: Add one optional, database-protected sale schedule to every SKU.

ALTER TABLE product_variants
    ADD COLUMN sale_price DECIMAL(15,2) NULL,
    ADD COLUMN sale_starts_at TIMESTAMP(6) NULL,
    ADD COLUMN sale_ends_at TIMESTAMP(6) NULL;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variant_sale_complete
        CHECK (
            (sale_price IS NULL AND sale_starts_at IS NULL AND sale_ends_at IS NULL)
            OR (sale_price IS NOT NULL AND sale_starts_at IS NOT NULL AND sale_ends_at IS NOT NULL)
        ),
    ADD CONSTRAINT ck_product_variant_sale_price
        CHECK (sale_price IS NULL OR (sale_price >= 0 AND sale_price < base_price)),
    ADD CONSTRAINT ck_product_variant_sale_window
        CHECK (sale_starts_at IS NULL OR sale_starts_at < sale_ends_at);
