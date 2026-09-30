package com.xdpsx.ecommerce.inventory.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    @Query("SELECT DISTINCT b.variant.product.id FROM InventoryBalance b "
            + "WHERE b.variant.product.id IN :productIds "
            + "AND b.variant.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND b.onHand > b.reserved")
    List<Long> findAvailableAdminProductIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT DISTINCT p.id FROM InventoryBalance b "
            + "JOIN b.variant v JOIN v.product p JOIN p.category c LEFT JOIN c.parent cp LEFT JOIN cp.parent cgp "
            + "LEFT JOIN cgp.parent cggp JOIN p.brand brand "
            + "WHERE p.id IN :productIds "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND p.published = true "
            + "AND brand.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND c.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (cp.id IS NULL OR cp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (cgp.id IS NULL OR cgp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND cggp.id IS NULL "
            + "AND NOT EXISTS (SELECT invalid.id FROM com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection invalid "
            + "WHERE invalid.variant = v AND (invalid.optionValue.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "OR invalid.optionValue.option.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE)) "
            + "AND b.onHand > b.reserved")
    List<Long> findAvailableStorefrontProductIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT b.variantId FROM InventoryBalance b "
            + "WHERE b.variant.product.id = :productId "
            + "AND b.variant.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND b.onHand > b.reserved")
    List<Long> findAvailableAdminVariantIdsByProductId(@Param("productId") Long productId);

    @Query("SELECT b.variantId FROM InventoryBalance b "
            + "JOIN b.variant v JOIN v.product p JOIN p.category c LEFT JOIN c.parent cp LEFT JOIN cp.parent cgp "
            + "LEFT JOIN cgp.parent cggp JOIN p.brand brand "
            + "WHERE p.id = :productId "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND p.published = true "
            + "AND brand.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND c.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (cp.id IS NULL OR cp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (cgp.id IS NULL OR cgp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND cggp.id IS NULL "
            + "AND NOT EXISTS (SELECT invalid.id FROM com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection invalid "
            + "WHERE invalid.variant = v AND (invalid.optionValue.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "OR invalid.optionValue.option.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE)) "
            + "AND b.onHand > b.reserved")
    List<Long> findAvailableStorefrontVariantIdsByProductId(@Param("productId") Long productId);

    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdWithVariant(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdForUpdate(@Param("variantId") Long variantId);
}
