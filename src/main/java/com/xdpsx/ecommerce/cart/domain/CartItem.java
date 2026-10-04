package com.xdpsx.ecommerce.cart.domain;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.common.persistence.AuditEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "cart_items",
        indexes = @Index(name = "ix_cart_item_variant", columnList = "variant_id"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "pk_cart_items",
                        columnNames = {"cart_id", "variant_id"}))
@EntityListeners(AuditingEntityListener.class)
public class CartItem extends AuditEntity {
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;

    @EmbeddedId
    private CartItemId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("cartId")
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("variantId")
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    /** Set by the Cart projection; availability is advisory and non-reserving. */
    @Transient
    private boolean available;

    @Transient
    private String availability;

    @PrePersist
    @PreUpdate
    private void validatePersistedQuantity() {
        if (quantity == null) throw new CartQuantityException("quantity must not be null");
        validateQuantity(quantity);
    }

    public void increaseBy(int amount) {
        validateQuantity(amount);
        if (quantity == null) throw new CartQuantityException("quantity must not be null");
        final int next;
        try {
            next = Math.addExact(quantity, amount);
        } catch (ArithmeticException exception) {
            throw new CartQuantityException("quantity overflow");
        }
        validateQuantity(next);
        quantity = next;
    }

    public void replaceQuantity(int replacement) {
        validateQuantity(replacement);
        quantity = replacement;
    }

    private static void validateQuantity(int value) {
        if (value < MIN_QUANTITY || value > MAX_QUANTITY) {
            throw new CartQuantityException("quantity must be between 1 and 99");
        }
    }
}
