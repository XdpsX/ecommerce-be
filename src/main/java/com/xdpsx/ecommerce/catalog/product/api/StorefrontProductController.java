package com.xdpsx.ecommerce.catalog.product.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.application.ProductService;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class StorefrontProductController implements StorefrontProductApiDocs {
    private final ProductService productService;

    @GetMapping
    public PageResponse<StorefrontProductSummaryResponse> getProducts(
            @Valid @ParameterObject StorefrontProductFilter filter) {
        return productService.getStorefrontProducts(filter);
    }

    @GetMapping("/slug/{slug}")
    public StorefrontProductDetailResponse getProductBySlug(@PathVariable String slug) {
        return productService.getStorefrontProductBySlug(slug);
    }

    @GetMapping("/filter-options")
    public List<ProductOptionResponse> getFilterOptions(
            @RequestParam(required = false) Integer categoryId, @RequestParam(required = false) Integer brandId) {
        return productService.getStorefrontFilterOptions(categoryId, brandId);
    }

    @GetMapping("/discount")
    public PageResponse<StorefrontProductSummaryResponse> getDiscountedProducts(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "8") int pageSize) {
        return productService.getDiscountedStorefrontProducts(pageNum, pageSize);
    }

    @GetMapping("/latest")
    public PageResponse<StorefrontProductSummaryResponse> getLatestProducts(
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "8") int pageSize) {
        return productService.getLatestStorefrontProducts(pageNum, pageSize);
    }
}
