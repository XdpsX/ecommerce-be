package com.xdpsx.ecommerce.catalog.category.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryHierarchyAnchor;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

public interface CategoryRepository extends JpaRepository<Category, Integer>, JpaSpecificationExecutor<Category> {
    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByParentId(Integer parentId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") Integer id);

    /**
     * Row-locked lookup used by every hierarchy mutation. Locking the node itself serializes two moves of the same
     * category before any parent or order is read.
     *
     * <p>{@code LEFT JOIN FETCH} cannot be combined with a lock query, so only the node is fetched and the parent is
     * resolved through the persistence context.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") Integer id);

    /**
     * Ordered sibling group of a non-root parent. The sort matches the {@code (parent_id, display_order)} index so
     * the range lock covers the group's key space and not the whole table.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findChildrenForUpdate(@Param("parentId") Integer parentId);

    /** Ordered root sibling group ({@code parent_id IS NULL}), locked for hierarchy mutations. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findRootsForUpdate();

    /**
     * Row-locked {@code id -> parentId} projection of one node, used to walk (and lock) the ancestor chain.
     *
     * <p>A projection rather than {@code SELECT c.parent} because a root row has a {@code null} parent and would
     * otherwise be indistinguishable from a missing row. Locking each ancestor is what makes the computed level stable
     * for the rest of the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c.id AS id, c.parent.id AS parentId FROM Category c WHERE c.id = :id")
    Optional<CategoryParentView> findParentViewForUpdate(@Param("id") Integer id);

    /**
     * {@code id -> parentId} projection of every category, used to compute a subtree height without mapping a
     * {@code children} collection. The taxonomy is intentionally small, so one flat projection is cheaper than a
     * recursive query and keeps this working on MySQL 8.
     */
    @Query("SELECT c.id AS id, c.parent.id AS parentId FROM Category c")
    List<CategoryParentView> findAllParentViews();

    /** Bounded projection used by the subtree-height and cycle checks. */
    interface CategoryParentView {
        Integer getId();

        Integer getParentId();
    }

    /**
     * Takes a transaction-scoped write lock on the hierarchy anchor row.
     *
     * <p>This row is what makes hierarchy writes serialize. It exists because a sibling group cannot always be locked
     * through its own rows: a root group is addressed by {@code parent_id IS NULL}, and an empty group has no row at
     * all. Locking one shared row gives every writer something to contend on.
     *
     * <p>The lock is a plain InnoDB row lock, so it is released exactly when the transaction commits or rolls back. That
     * is why this is used instead of {@code GET_LOCK}: a MySQL named lock is bound to the physical session and would
     * survive the transaction, which leaks a held lock into the connection pool.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CategoryHierarchyAnchor a WHERE a.id = 1")
    CategoryHierarchyAnchor lockHierarchyAnchor();

    /**
     * Whether the anchor row exists. A dedicated query is needed because {@code existsById} would check the
     * {@code categories} table, and the anchor shares the id space by accident rather than by relation.
     */
    @Query("SELECT COUNT(a) > 0 FROM CategoryHierarchyAnchor a WHERE a.id = 1")
    boolean hierarchyAnchorExists();

    /**
     * Storefront lookup. Named for the stored status it filters on; effective status across the ancestor chain
     * is not implemented yet.
     */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatusWithParent(@Param("id") Integer id, @Param("status") CategoryStatus status);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatus(@Param("id") Integer id, @Param("status") CategoryStatus status);

    @Query(value = """
		SELECT COUNT(*) FROM (
			SELECT category_id FROM category_brands WHERE category_id = ?1
			UNION ALL
			SELECT category_id FROM products WHERE category_id = ?1
		) AS combined
	""", nativeQuery = true)
    long countCategoriesInOtherTables(Integer categoryId);
}
