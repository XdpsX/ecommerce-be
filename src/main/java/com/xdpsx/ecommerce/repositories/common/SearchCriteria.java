package com.xdpsx.ecommerce.repositories.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchCriteria {
    private String key;
    private Object value;
    private SearchOperator operator;
}
