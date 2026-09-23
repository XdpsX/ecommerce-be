package com.xdpsx.ecommerce.cart.domain;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.common.persistence.AuditEntity;
import com.xdpsx.ecommerce.user.domain.User;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "cart_items")
@EntityListeners(AuditingEntityListener.class)
public class CartItem extends AuditEntity {
    @EmbeddedId
    private CartItemId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer quantity;
}
