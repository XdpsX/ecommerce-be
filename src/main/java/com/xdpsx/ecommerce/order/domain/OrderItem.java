package com.xdpsx.ecommerce.order.domain;

import jakarta.persistence.*;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(nullable = false, length = 128)
    private String sku;

    @Column(name = "variant_description", nullable = false, length = 1000)
    private String variantDescription;

    @Column(name = "unit_base_price", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal unitBasePrice;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "final_unit_price", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal finalUnitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 20, scale = 2)
    private java.math.BigDecimal subtotal;

    @Column(nullable = false, length = 3)
    private String currency;
}
