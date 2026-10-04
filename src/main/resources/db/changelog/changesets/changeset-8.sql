-- liquibase formatted sql

-- changeset xdpsx:brand-cr1-model-and-concurrency
-- comment: Replace brands.public_flg with a BrandStatus enum column and add a JPA optimistic-lock version.
-- The new columns are added nullable first so existing rows can be backfilled before the NOT NULL
-- constraints are enforced. Lifecycle is a direct mapping of the old visibility flag: public_flg = TRUE
-- becomes ACTIVE, everything else becomes INACTIVE. version starts at 0 for every existing row.

ALTER TABLE brands
ADD COLUMN status VARCHAR(16) NULL AFTER name,
ADD COLUMN version BIGINT NULL DEFAULT 0 AFTER status;

UPDATE brands
SET status = CASE
    WHEN public_flg = TRUE THEN 'ACTIVE'
    ELSE 'INACTIVE'
END,
version = 0;

ALTER TABLE brands
MODIFY COLUMN status VARCHAR(16) NOT NULL,
MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE brands
DROP COLUMN public_flg;
