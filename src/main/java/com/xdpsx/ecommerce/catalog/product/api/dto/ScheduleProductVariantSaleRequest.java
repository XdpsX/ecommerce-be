package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ScheduleProductVariantSaleRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000000.00") @Digits(integer = 10, fraction = 2)
        BigDecimal salePrice,

        @NotBlank @Pattern(regexp = "VND", message = "currency must be VND")
        String currency,

        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {}
