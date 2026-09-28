package com.xdpsx.ecommerce.catalog.variantoption.api.dto;

import java.util.List;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;

public record VariantOptionResponse(
        Long id,
        String code,
        String name,
        Integer displayOrder,
        VariantOptionStatus status,
        List<VariantOptionValueResponse> values) {}
