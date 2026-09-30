package com.xdpsx.ecommerce.cart.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {
    @Query("SELECT DISTINCT ci FROM CartItem ci "
            + "JOIN FETCH ci.variant v JOIN FETCH v.product p "
            + "LEFT JOIN FETCH p.category c LEFT JOIN FETCH c.parent cp LEFT JOIN FETCH cp.parent cgp "
            + "LEFT JOIN FETCH cgp.parent cggp LEFT JOIN FETCH p.brand b "
            + "LEFT JOIN FETCH v.selections s LEFT JOIN FETCH s.optionValue value LEFT JOIN FETCH value.option option "
            + "WHERE ci.user.id = :userId ORDER BY ci.createdAt DESC")
    List<CartItem> findNewestByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT ci FROM CartItem ci JOIN FETCH ci.variant v JOIN FETCH v.product p "
            + "JOIN p.category c LEFT JOIN c.parent cp LEFT JOIN cp.parent cgp LEFT JOIN cgp.parent cggp JOIN p.brand b "
            + "LEFT JOIN FETCH v.selections s LEFT JOIN FETCH s.optionValue value LEFT JOIN FETCH value.option option "
            + "WHERE ci.user.id = :userId AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND p.published = true "
            + "AND b.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND c.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (cp.id IS NULL OR cp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (cgp.id IS NULL OR cgp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND cggp.id IS NULL "
            + "AND NOT EXISTS (SELECT invalid.id FROM ProductVariantSelection invalid "
            + "WHERE invalid.variant = v AND (invalid.optionValue.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "OR invalid.optionValue.option.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE)) "
            + "AND EXISTS (SELECT balance.variantId FROM InventoryBalance balance "
            + "WHERE balance.variant = v AND balance.onHand > balance.reserved) ORDER BY ci.createdAt DESC")
    List<CartItem> findInStockCartByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT ci.variant.id FROM CartItem ci "
            + "JOIN ci.variant v JOIN v.product p JOIN p.category c LEFT JOIN c.parent cp LEFT JOIN cp.parent cgp "
            + "LEFT JOIN cgp.parent cggp JOIN p.brand b "
            + "WHERE ci.user.id = :userId "
            + "AND v.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND p.published = true "
            + "AND b.status = com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus.ACTIVE "
            + "AND c.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE "
            + "AND (cp.id IS NULL OR cp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND (cgp.id IS NULL OR cgp.status = com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus.ACTIVE) "
            + "AND cggp.id IS NULL "
            + "AND NOT EXISTS (SELECT invalid.id FROM ProductVariantSelection invalid "
            + "WHERE invalid.variant = v AND (invalid.optionValue.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE "
            + "OR invalid.optionValue.option.status <> "
            + "com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus.ACTIVE)) "
            + "AND EXISTS (SELECT balance.variantId FROM InventoryBalance balance "
            + "WHERE balance.variant = v AND balance.onHand > balance.reserved)")
    List<Long> findAvailableEligibleVariantIdsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
