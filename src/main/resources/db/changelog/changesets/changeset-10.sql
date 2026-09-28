-- liquibase formatted sql

-- changeset xdpsx:variant-option-cr1-dictionary
-- comment: Add the shared option/value dictionary used by Product Variants.

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
