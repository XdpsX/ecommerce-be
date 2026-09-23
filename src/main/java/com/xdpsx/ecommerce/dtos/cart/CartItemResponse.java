package com.xdpsx.ecommerce.dtos.cart;

import com.xdpsx.ecommerce.dtos.product.ProductResponse;

import lombok.Data;

@Data
public class CartItemResponse {
    private Integer quantity;
    private ProductResponse product;
}
