package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record UpdateProductVariantPriceRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000000.00") @Digits(integer = 10, fraction = 2)
                BigDecimal basePrice) {}
