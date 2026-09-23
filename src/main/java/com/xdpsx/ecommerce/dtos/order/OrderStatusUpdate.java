package com.xdpsx.ecommerce.dtos.order;

import jakarta.validation.constraints.NotNull;

import com.xdpsx.ecommerce.entities.enums.OrderStatus;

import lombok.Data;

@Data
public class OrderStatusUpdate {
    @NotNull
    private OrderStatus status;
}
