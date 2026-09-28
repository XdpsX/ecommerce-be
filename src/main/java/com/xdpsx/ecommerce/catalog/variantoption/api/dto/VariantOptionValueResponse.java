package com.xdpsx.ecommerce.catalog.variantoption.api.dto;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;

public record VariantOptionValueResponse(
        Long id, String code, String name, Integer displayOrder, VariantOptionStatus status) {}
