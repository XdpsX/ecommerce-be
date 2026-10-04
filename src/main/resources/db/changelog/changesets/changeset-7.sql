-- liquibase formatted sql

-- changeset xdpsx:category-cr2-hierarchy-anchor
-- comment: Add a single-row anchor table used to serialize Category hierarchy writes.
-- A sibling group cannot always be locked through its own rows: the root group is addressed by parent_id IS NULL
-- and an empty group has no row at all. Locking this one shared row (SELECT ... FOR UPDATE) gives every hierarchy
-- writer something to contend on, and the row lock is released by commit or rollback.

CREATE TABLE category_hierarchy_anchor (
    id INT NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO category_hierarchy_anchor (id) VALUES (1);