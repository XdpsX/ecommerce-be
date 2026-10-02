package com.xdpsx.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "inventory_balances")
public class InventoryBalance {
    @Id
    @Column(name = "variant_id")
    private Long variantId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "on_hand", nullable = false)
    private long onHand;

    @Column(nullable = false)
    private long reserved;

    private InventoryBalance(ProductVariant variant, long onHand, long reserved) {
        this.variant = variant;
        this.onHand = onHand;
        this.reserved = reserved;
    }

    public Long getVariantId() {
        return variantId != null ? variantId : variant == null ? null : variant.getId();
    }

    public static InventoryBalance zero(ProductVariant variant) {
        return new InventoryBalance(variant, 0, 0);
    }

    public long available() {
        return Math.subtractExact(onHand, reserved);
    }

    public void reserve(long quantity) {
        validatePositive(quantity);
        if (available() < quantity) {
            throw new InventoryBalanceAdjustmentException("not enough inventory available");
        }
        reserved = Math.addExact(reserved, quantity);
    }

    public void release(long quantity) {
        validatePositive(quantity);
        if (reserved < quantity) {
            throw new InventoryBalanceAdjustmentException("cannot release more inventory than reserved");
        }
        reserved -= quantity;
    }

    public void consumeReserved(long quantity) {
        validatePositive(quantity);
        if (reserved < quantity || onHand < quantity) {
            throw new InventoryBalanceAdjustmentException("cannot consume more inventory than reserved");
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    public void adjustOnHand(long quantityDelta) {
        final long nextOnHand;
        try {
            nextOnHand = Math.addExact(onHand, quantityDelta);
        } catch (ArithmeticException exception) {
            throw new InventoryBalanceAdjustmentException("onHand adjustment overflow", exception);
        }
        if (nextOnHand < 0) {
            throw new InventoryBalanceAdjustmentException("onHand cannot be negative");
        }
        if (nextOnHand < reserved) {
            throw new InventoryBalanceAdjustmentException("onHand cannot be lower than reserved");
        }
        onHand = nextOnHand;
    }

    private static void validatePositive(long quantity) {
        if (quantity <= 0) throw new InventoryBalanceAdjustmentException("quantity must be positive");
    }
}
