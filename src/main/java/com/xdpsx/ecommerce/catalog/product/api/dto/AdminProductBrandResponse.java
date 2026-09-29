package com.xdpsx.ecommerce.catalog.product.api.dto;

import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;

public record AdminProductBrandResponse(Integer id, String name, BrandStatus status) {}
