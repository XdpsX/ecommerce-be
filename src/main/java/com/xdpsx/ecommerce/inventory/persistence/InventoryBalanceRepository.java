package com.xdpsx.ecommerce.inventory.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdWithVariant(@Param("variantId") Long variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b JOIN FETCH b.variant WHERE b.variantId = :variantId")
    Optional<InventoryBalance> findByVariantIdForUpdate(@Param("variantId") Long variantId);
}
