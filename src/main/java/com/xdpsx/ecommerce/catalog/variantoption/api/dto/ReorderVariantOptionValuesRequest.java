package com.xdpsx.ecommerce.catalog.variantoption.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReorderVariantOptionValuesRequest(@NotEmpty List<@NotNull Long> valueIds) {}
