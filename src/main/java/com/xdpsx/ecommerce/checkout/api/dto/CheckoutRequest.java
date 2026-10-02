package com.xdpsx.ecommerce.checkout.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CheckoutRequest {
    @NotNull
    @Positive
    private Long addressId;

    @Size(max = 500)
    private String description;
}
