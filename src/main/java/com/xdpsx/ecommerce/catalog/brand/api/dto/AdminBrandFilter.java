package com.xdpsx.ecommerce.catalog.brand.api.dto;

import com.xdpsx.ecommerce.common.pagination.AbstractPageParams;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
