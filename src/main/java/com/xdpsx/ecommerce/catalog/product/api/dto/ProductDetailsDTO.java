package com.xdpsx.ecommerce.catalog.product.api.dto;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class ProductDetailsDTO extends ProductResponse {
    private String description;
    private List<ProductImageDTO> images;
}
