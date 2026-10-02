package com.xdpsx.ecommerce.cart.api.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private List<CartItemResponse> items = List.of();
    private int distinctItemCount;
    private int totalQuantity;
    private BigDecimal estimatedSubtotal = BigDecimal.ZERO.setScale(2);
    private String currency;
}
