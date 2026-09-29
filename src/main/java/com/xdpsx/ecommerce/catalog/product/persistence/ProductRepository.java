package com.xdpsx.ecommerce.catalog.product.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.catalog.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    boolean existsBySlug(String slug);

    boolean existsByBrandId(Integer brandId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand "
            + "LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media WHERE p.id = :id")
    Optional<Product> findProductById(Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category c "
            + "LEFT JOIN FETCH c.parent cp LEFT JOIN FETCH cp.parent cgp LEFT JOIN FETCH cgp.parent cggp "
            + "LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media "
            + "WHERE p.id = :id")
    Optional<Product> findAdminProductById(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand "
            + "LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media WHERE p.slug = :slug")
    Optional<Product> findProductBySlug(String slug);

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.category c "
            + "LEFT JOIN FETCH c.parent cp LEFT JOIN FETCH cp.parent cgp LEFT JOIN FETCH cgp.parent cggp "
            + "JOIN FETCH p.brand b LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media "
            + "WHERE p.slug = :slug AND p.published = true "
            + "AND b.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND c.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (cp.id IS NULL OR cp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (cgp.id IS NULL OR cgp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND cggp.id IS NULL "
            + "AND EXISTS (SELECT v.id FROM ProductVariant v WHERE v.product.id = p.id "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE)")
    Optional<Product> findStorefrontProductBySlug(@Param("slug") String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category c "
            + "LEFT JOIN FETCH c.parent cp LEFT JOIN FETCH cp.parent cgp LEFT JOIN FETCH cgp.parent cggp "
            + "LEFT JOIN FETCH p.brand "
            + "LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media "
            + "WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findAllWithImagesByIdIn(@Param("ids") Collection<Long> ids);
}
