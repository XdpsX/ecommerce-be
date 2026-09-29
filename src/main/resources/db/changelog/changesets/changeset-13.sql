-- liquibase formatted sql

-- changeset xdpsx:storefront-inventory-availability
-- comment: Remove the mutable Product-level stock flag; Inventory is the source of truth.

ALTER TABLE products DROP COLUMN in_stock;
