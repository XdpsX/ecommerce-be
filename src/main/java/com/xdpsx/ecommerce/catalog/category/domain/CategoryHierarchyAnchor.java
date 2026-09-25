package com.xdpsx.ecommerce.catalog.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

/**
 * Single-row anchor that serializes Category hierarchy writes.
 *
 * <p>A sibling group cannot always be locked through its own rows: the root group is addressed by
 * {@code parent_id IS NULL} and an empty group has no row at all. Locking one shared row
 * ({@code SELECT ... FOR UPDATE}) gives every writer something to contend on, and because it is a plain InnoDB row
 * lock it is released exactly when the transaction ends.
 *
 * <p>The table holds exactly one row and is never updated. It exists only to be locked.
 */
@Getter
@Entity
@Table(name = "category_hierarchy_anchor")
public class CategoryHierarchyAnchor {
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(nullable = false, updatable = false)
    private Integer id;
}
