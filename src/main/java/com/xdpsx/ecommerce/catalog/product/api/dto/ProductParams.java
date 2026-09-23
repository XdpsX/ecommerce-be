package com.xdpsx.ecommerce.catalog.product.api.dto;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import jakarta.validation.constraints.Min;

import com.xdpsx.ecommerce.catalog.product.api.validation.SortConstraint;
import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class ProductParams extends AbstractPageParams {
    private String search;

    @SortConstraint(fields = {FIELD_NAME, FIELD_DATE, FIELD_PRICE})
    private String sort;

    @Min(value = 0)
    private Double minPrice;

    @Min(value = 0)
    private Double maxPrice;

    private Boolean hasPublished;
    private Boolean hasDiscount;
    private Boolean inStock;
    private Integer categoryId;
    private Integer brandId;
}
