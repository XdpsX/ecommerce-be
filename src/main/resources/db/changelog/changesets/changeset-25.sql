-- liquibase formatted sql

-- changeset xdpsx:category-optimistic-version
-- comment: Add a server-issued optimistic-lock version to Category.

ALTER TABLE categories
    ADD COLUMN version BIGINT NULL DEFAULT 0;

UPDATE categories
SET version = 0
WHERE version IS NULL;

ALTER TABLE categories
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;
