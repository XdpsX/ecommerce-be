package com.xdpsx.ecommerce.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartResponse {
    private List<CartItemResponse> items;
    private int distinctItemCount;
    private int totalQuantity;
    private BigDecimal estimatedSubtotal;
    private String currency;

    public CartResponse() {
        this(List.of(), 0, 0, BigDecimal.ZERO.setScale(2), null);
    }

    public CartResponse(
            List<CartItemResponse> items,
            int distinctItemCount,
            int totalQuantity,
            BigDecimal estimatedSubtotal,
            String currency) {
        this.items = items;
        this.distinctItemCount = distinctItemCount;
        this.totalQuantity = totalQuantity;
        this.estimatedSubtotal = estimatedSubtotal;
        this.currency = currency;
    }
}
