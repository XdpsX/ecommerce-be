package com.xdpsx.ecommerce.catalog.brand.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;

public interface BrandRepository extends JpaRepository<Brand, Integer>, JpaSpecificationExecutor<Brand> {
    boolean existsByName(String name);

    @Query("SELECT b FROM Brand b JOIN b.categories c WHERE c.id = :categoryId ORDER BY b.name")
    List<Brand> findBrandsByCategoryId(Integer categoryId);

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.categories WHERE b.id = :id")
    Optional<Brand> findDetailById(@Param("id") Integer id);

    //    @Query(value =
    //            "SELECT COUNT(*) FROM (" +
    //                "SELECT brand_id FROM category_brands WHERE brand_id = ?1 " +
    //            ") AS combined"
    //            , nativeQuery = true)
    //    long countBrandsInOtherTables(Integer brandId);

}
