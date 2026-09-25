package com.xdpsx.ecommerce.catalog.category.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

public interface CategoryRepository extends JpaRepository<Category, Integer>, JpaSpecificationExecutor<Category> {
    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByParentId(Integer parentId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Category> findByIdWithParent(@Param("id") Integer id);

    /**
     * Storefront lookup. Named for the stored status it filters on; effective status across the ancestor chain
     * is not implemented yet.
     */
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatusWithParent(@Param("id") Integer id, @Param("status") CategoryStatus status);

    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.status = :status")
    Optional<Category> findByIdAndStatus(@Param("id") Integer id, @Param("status") CategoryStatus status);

    /**
     * Highest order currently used in a sibling group, or {@code -1} when the group is empty. A create appends
     * by using {@code max + 1}. Roots are addressed with {@code parentId == null}.
     */
    @Query("SELECT COALESCE(MAX(c.displayOrder), -1) FROM Category c WHERE c.parent.id = :parentId")
    int findMaxDisplayOrderByParentId(@Param("parentId") Integer parentId);

    @Query("SELECT COALESCE(MAX(c.displayOrder), -1) FROM Category c WHERE c.parent IS NULL")
    int findMaxDisplayOrderForRoots();

    @Query(value = """
		SELECT COUNT(*) FROM (
			SELECT category_id FROM category_brands WHERE category_id = ?1
			UNION ALL
			SELECT category_id FROM products WHERE category_id = ?1
		) AS combined
	""", nativeQuery = true)
    long countCategoriesInOtherTables(Integer categoryId);
}
