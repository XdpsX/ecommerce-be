package com.xdpsx.ecommerce.dtos.product;

import static com.xdpsx.ecommerce.constants.FieldConstants.*;

import jakarta.validation.constraints.Min;

import com.xdpsx.ecommerce.dtos.common.AbstractPageParams;
import com.xdpsx.ecommerce.validations.SortConstraint;

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
