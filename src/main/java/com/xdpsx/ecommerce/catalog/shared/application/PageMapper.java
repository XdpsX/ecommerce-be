package com.xdpsx.ecommerce.catalog.shared.application;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.common.pagination.PageResponse;

@Component
public class PageMapper {
    public static <T, R> PageResponse<R> toPageResponse(Page<T> page, Function<T, R> mapper) {
        List<R> items = page.getContent().stream().map(mapper).collect(Collectors.toList());
        return PageResponse.of(
                items, page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
