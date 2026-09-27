package com.xdpsx.ecommerce.catalog.brand.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;

public interface BrandRepository extends JpaRepository<Brand, Integer>, JpaSpecificationExecutor<Brand> {
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    /**
     * Batch-fetches the categories collection for one resolved page of Brands. The page query itself must not
     * fetch the collection (that would paginate in memory); this single query loads the associations for every
     * Brand in the page, so mapping the page costs one extra query regardless of page size.
     */
    @Query("SELECT DISTINCT b FROM Brand b LEFT JOIN FETCH b.categories WHERE b IN :brands")
    List<Brand> fetchCategories(@Param("brands") List<Brand> brands);

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.image LEFT JOIN FETCH b.categories WHERE b.id = :id")
    Optional<Brand> findDetailById(@Param("id") Integer id);
}
