package com.xdpsx.ecommerce.mappers;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.dtos.product.ProductResponse;
import com.xdpsx.ecommerce.entities.Product;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PageMapper {
    private final ProductMapper productMapper;

    public PageResponse<ProductResponse> toProductPageResponse(Page<Product> productPage) {
        return toPageResponse(productPage, productMapper::fromEntityToResponse);
    }

    public static <T, R> PageResponse<R> toPageResponse(Page<T> page, Function<T, R> mapper) {
        List<R> items = page.getContent().stream().map(mapper).collect(Collectors.toList());
        return PageResponse.<R>builder()
                .items(items)
                .pageNum(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
