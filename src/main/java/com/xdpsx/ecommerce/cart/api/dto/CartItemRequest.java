package com.xdpsx.ecommerce.cart.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class CartItemRequest {
    @NotNull(message = "Variant ID is required")
    private Long variantId;

    @JsonIgnore
    public Long getProductId() {
        return variantId;
    }

    @JsonIgnore
    public void setProductId(Long productId) {
        this.variantId = productId;
    }

    @NotNull(message = "Quantity is required")
    @Min(1)
    private Integer quantity;
}
