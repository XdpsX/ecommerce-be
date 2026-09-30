package com.xdpsx.ecommerce.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.inventory.api.validation.NonZero;

public record InventoryAdjustmentRequest(
        @NotNull @NonZero(message = "must not be zero") Long quantityDelta,
        @NotBlank @Size(max = 500) String reason) {}
