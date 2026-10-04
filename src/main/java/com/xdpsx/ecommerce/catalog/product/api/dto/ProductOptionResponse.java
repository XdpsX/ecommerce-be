package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

public record ProductOptionResponse(
        Long id, String code, String name, Integer displayOrder, List<ProductOptionValueResponse> values) {}
