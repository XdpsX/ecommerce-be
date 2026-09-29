package com.xdpsx.ecommerce.cart.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {
    @Query("SELECT ci FROM CartItem ci WHERE ci.user.id = :userId " + "ORDER BY ci.createdAt DESC")
    List<CartItem> findNewestByUserId(@Param("userId") Long userId);

    @Query("SELECT ci FROM CartItem ci WHERE ci.user.id = :userId "
            + "AND EXISTS (SELECT b.variantId FROM InventoryBalance b "
            + "WHERE b.variant.product = ci.product "
            + "AND b.variant.status = com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus.ACTIVE "
            + "AND b.onHand > b.reserved) ORDER BY ci.createdAt DESC")
    List<CartItem> findInStockCartByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
