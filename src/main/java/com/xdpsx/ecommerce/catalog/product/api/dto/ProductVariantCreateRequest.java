package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.*;

public record ProductVariantCreateRequest(
        @NotBlank @Size(max = 128) String sku,
        @Size(max = 128) String barcode,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000000.00") @Digits(integer = 10, fraction = 2)
                BigDecimal basePrice,
        @NotNull @Size(max = 16) List<@NotNull Long> optionValueIds) {}
