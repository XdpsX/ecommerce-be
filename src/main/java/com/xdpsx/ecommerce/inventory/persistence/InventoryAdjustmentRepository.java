package com.xdpsx.ecommerce.inventory.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.inventory.domain.InventoryAdjustment;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {
    List<InventoryAdjustment> findAllByVariantIdOrderByCreatedAtAscIdAsc(Long variantId);
}
