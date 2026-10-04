package com.xdpsx.ecommerce.catalog.category.api.dto;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.FIELD_DATE;
import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.FIELD_NAME;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.shared.api.validation.SortConstraint;
import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AdminCategoryFilter extends AbstractPageParams {
    private String name;
    private CategoryStatus status;
    private Integer parentId;

    @SortConstraint(fields = {FIELD_NAME, FIELD_DATE})
    private String sort;

    @Min(1)
    @Max(Category.MAX_DEPTH)
    private Integer level;
}
