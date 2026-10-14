-- liquibase formatted sql

-- changeset ecommerce:product-variants-baseline
-- comment: Squashed product-variants schema for fresh development databases.

CREATE TABLE variant_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_variant_option_code UNIQUE (code),
    INDEX ix_variant_option_status_order (status, display_order, id)
);

CREATE TABLE variant_option_values (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    display_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_variant_option_value_code UNIQUE (option_id, code),
    CONSTRAINT uk_variant_option_value_order UNIQUE (option_id, display_order),
    CONSTRAINT fk_variant_option_value_option
        FOREIGN KEY (option_id) REFERENCES variant_options(id) ON DELETE RESTRICT,
    INDEX ix_variant_option_value_status_order (option_id, status, display_order, id)
);

ALTER TABLE variant_option_values
    ADD CONSTRAINT uk_variant_option_value_option_id UNIQUE (option_id, id);

CREATE TABLE product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(128) NOT NULL,
    barcode VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL,
    combination_key VARCHAR(700) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_variant_sku UNIQUE (sku),
    CONSTRAINT uk_product_variant_barcode UNIQUE (barcode),
    CONSTRAINT uk_product_variant_combination UNIQUE (product_id, combination_key),
    CONSTRAINT fk_product_variant_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX ix_product_variant_product_status (product_id, status)
);

CREATE TABLE product_variant_selections (
    variant_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    option_value_id BIGINT NOT NULL,
    PRIMARY KEY (variant_id, option_id),
    CONSTRAINT fk_product_variant_selection_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_variant_selection_option_value
        FOREIGN KEY (option_id, option_value_id)
        REFERENCES variant_option_values(option_id, id) ON DELETE RESTRICT,
    INDEX ix_variant_selection_option_value (option_value_id, variant_id)
);

ALTER TABLE product_variants
    ADD COLUMN base_price DECIMAL(15, 2) NULL;

ALTER TABLE product_variants
    MODIFY COLUMN base_price DECIMAL(15, 2) NOT NULL;

ALTER TABLE product_variants
    ADD CONSTRAINT ck_product_variant_base_price_range
        CHECK (base_price >= 0.00 AND base_price <= 1000000000.00);

ALTER TABLE products
    MODIFY COLUMN price DECIMAL(15, 2) NOT NULL DEFAULT 0.00;

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


