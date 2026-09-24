package com.xdpsx.ecommerce.common.pagination;

import java.util.List;

public record PageResponse<T>(List<T> data, PageMetadata meta) {

    public PageResponse {
        data = List.copyOf(data);
    }

    public static <T> PageResponse<T> of(List<T> data, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(data, new PageMetadata(page, size, totalElements, totalPages));
    }

    public record PageMetadata(int page, int size, long totalElements, int totalPages) {}
}
