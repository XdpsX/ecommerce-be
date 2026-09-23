package com.xdpsx.ecommerce.common.pagination;

import java.util.Collection;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {
    private Collection<T> items;
    private int pageNum;
    private int pageSize;
    private long totalItems;
    private int totalPages;
}
