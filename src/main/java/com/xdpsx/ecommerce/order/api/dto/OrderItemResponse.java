package com.xdpsx.ecommerce.order.api.dto;

import com.xdpsx.ecommerce.catalog.product.api.dto.ProductResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private ProductResponse product;
    private Integer quantity;
}
