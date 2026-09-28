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

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand "
            + "LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media WHERE p.slug = :slug")
    Optional<Product> findProductBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.brand "
            + "LEFT JOIN FETCH p.images i LEFT JOIN FETCH i.media "
            + "WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findAllWithImagesByIdIn(@Param("ids") Collection<Long> ids);
}
