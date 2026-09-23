package com.xdpsx.ecommerce.order.api.dto;

import jakarta.validation.constraints.NotNull;

import com.xdpsx.ecommerce.order.domain.OrderStatus;

import lombok.Data;

@Data
public class OrderStatusUpdate {
    @NotNull
    private OrderStatus status;
}
