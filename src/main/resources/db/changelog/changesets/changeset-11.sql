-- liquibase formatted sql

-- changeset xdpsx:product-variant-cr2-foundation
-- comment: Add immutable Product Variant/SKU identity and option selections.

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
