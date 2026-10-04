package com.xdpsx.ecommerce.catalog.product.api.dto;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.catalog.shared.api.validation.SortConstraint;
import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StorefrontProductFilter extends AbstractPageParams {
    private String search;

    @SortConstraint(fields = {FIELD_NAME, FIELD_DATE, FIELD_PRICE})
    private String sort;

    @Min(0)
    private BigDecimal minPrice;

    @Min(0)
    private BigDecimal maxPrice;

    private Boolean inStock;
    private Integer categoryId;
    private Integer brandId;

    @Size(max = 50)
    private List<Long> optionValueIds;
}
