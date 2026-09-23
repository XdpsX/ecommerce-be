package com.xdpsx.ecommerce.dtos.brand;

import com.xdpsx.ecommerce.dtos.common.AbstractPageParams;

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
