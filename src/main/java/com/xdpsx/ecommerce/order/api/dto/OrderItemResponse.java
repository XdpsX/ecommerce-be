package com.xdpsx.ecommerce.order.api.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long variantId;
    private String sku;
    private String variantDescription;
    private BigDecimal unitBasePrice;
    private BigDecimal discountAmount;
    private BigDecimal finalUnitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private String currency;
}
