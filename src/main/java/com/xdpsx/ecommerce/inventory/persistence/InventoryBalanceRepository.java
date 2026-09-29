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
    List<Long> findAvailableProductIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT b.variantId FROM InventoryBalance b "
            + "WHERE b.variant.product.id = :productId "
            + "AND b.variant.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND b.onHand > b.reserved")
    List<Long> findAvailableVariantIdsByProductId(@Param("productId") Long productId);

    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdWithVariant(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdForUpdate(@Param("variantId") Long variantId);
}
