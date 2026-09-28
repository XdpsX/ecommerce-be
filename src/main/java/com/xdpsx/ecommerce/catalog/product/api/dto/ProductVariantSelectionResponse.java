package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

public record ProductVariantSelectionResponse(Long variantId, String sku, List<Long> optionValueIds) {}
