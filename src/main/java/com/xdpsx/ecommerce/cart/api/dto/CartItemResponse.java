package com.xdpsx.ecommerce.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class CartItemResponse {
    private Long productId;
    private String productName;
    private Long variantId;
    private String sku;
    private List<Long> optionValueIds;
    private Integer quantity;
    private BigDecimal basePrice;
    private BigDecimal discountAmount;
    private BigDecimal finalUnitPrice;
    private String currency;
    private boolean available;
}
