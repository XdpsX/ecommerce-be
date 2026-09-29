-- liquibase formatted sql

-- changeset xdpsx:inventory-cr1-foundation
-- comment: Add SKU inventory balances and immutable adjustment audit records.

CREATE TABLE inventory_balances (
    variant_id BIGINT NOT NULL PRIMARY KEY,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_balance_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    CONSTRAINT ck_inventory_balance_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_balance_reserved_non_negative CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_balance_reserved_not_above_on_hand CHECK (reserved <= on_hand)
);

CREATE TABLE inventory_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    quantity_delta BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    performed_by VARCHAR(320) NOT NULL,
    on_hand_after BIGINT NOT NULL,
    reserved_after BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_adjustment_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT,
    INDEX ix_inventory_adjustment_variant_created (variant_id, created_at, id)
);

INSERT INTO inventory_balances (variant_id, on_hand, reserved)
SELECT id, 0, 0
FROM product_variants;
