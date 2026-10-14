package com.xdpsx.ecommerce.catalog.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProductImageDTO(
        String id,

        @Schema(
                description = "Image URL; storefront product detail uses productDetail when processing is READY, "
                        + "while admin detail uses the original URL. Non-ready media uses the original URL.")
        String url,

        int displayOrder) {}
