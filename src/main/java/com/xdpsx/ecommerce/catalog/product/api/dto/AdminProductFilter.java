package com.xdpsx.ecommerce.catalog.product.api.dto;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import com.xdpsx.ecommerce.catalog.shared.api.validation.SortConstraint;
import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AdminProductFilter extends AbstractPageParams {
    private String search;
    private Boolean hasPublished;

    @SortConstraint(fields = {FIELD_NAME, FIELD_DATE, FIELD_PRICE})
    private String sort;
}
