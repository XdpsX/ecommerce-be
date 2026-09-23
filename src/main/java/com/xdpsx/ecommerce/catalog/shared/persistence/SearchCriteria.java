package com.xdpsx.ecommerce.catalog.shared.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchCriteria {
    private String key;
    private Object value;
    private SearchOperator operator;
}
