package com.xdpsx.ecommerce.cart.api.dto;

import com.xdpsx.ecommerce.catalog.product.api.dto.ProductResponse;

import lombok.Data;

@Data
public class CartItemResponse {
    private Integer quantity;
    private ProductResponse product;
}
