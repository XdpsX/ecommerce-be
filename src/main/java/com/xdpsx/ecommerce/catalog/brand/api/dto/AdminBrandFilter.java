package com.xdpsx.ecommerce.catalog.brand.api.dto;

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
    private Boolean publicFlg;
    private String sort;
}
