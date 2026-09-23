package com.xdpsx.ecommerce.catalog.category.api.dto;

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
    private Boolean publicFlg;
    private String sort;
    private Integer level;
}
