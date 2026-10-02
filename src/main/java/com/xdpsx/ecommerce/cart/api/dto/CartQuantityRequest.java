package com.xdpsx.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CartQuantityRequest {
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be between 1 and 99")
    @Max(value = 99, message = "Quantity must be between 1 and 99")
    private Integer quantity;
}
