package com.xdpsx.ecommerce.dtos.order;

import com.xdpsx.ecommerce.dtos.product.ProductResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponse {
    private Long id;
    private ProductResponse product;
    private Integer quantity;
}
