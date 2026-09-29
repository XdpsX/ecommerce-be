package com.xdpsx.ecommerce.catalog.product.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateProductPublicationRequest(@NotNull Boolean published) {}
