package com.xdpsx.ecommerce.dtos.common;

import static com.xdpsx.ecommerce.constants.PageConstants.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class AbstractPageParams {
    @Min(value = 1)
    @Builder.Default
    private Integer pageNum = 1;

    @Min(value = MIN_ITEMS_PER_PAGE)
    @Max(value = MAX_ITEMS_PER_PAGE)
    @Builder.Default
    private Integer pageSize = DEFAULT_ITEMS_PER_PAGE;
}
