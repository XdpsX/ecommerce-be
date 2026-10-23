-- liquibase formatted sql

-- changeset ecommerce:category-actor-audit
-- comment: Records the authenticated account that created and last changed each category; historical actors remain unknown.
ALTER TABLE categories
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;
