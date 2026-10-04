package com.xdpsx.ecommerce.catalog.brand.api.dto;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.FIELD_DATE;
import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.FIELD_NAME;

import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.shared.api.validation.SortConstraint;
import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AdminBrandFilter extends AbstractPageParams {
    private String name;
    private BrandStatus status;

    @SortConstraint(fields = {FIELD_NAME, FIELD_DATE})
    private String sort;
}
