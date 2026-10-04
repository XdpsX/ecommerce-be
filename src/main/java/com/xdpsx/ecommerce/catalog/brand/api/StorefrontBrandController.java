package com.xdpsx.ecommerce.catalog.brand.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;

import lombok.RequiredArgsConstructor;

/** Public Brand read boundary. Only active Brands are exposed. */
@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class StorefrontBrandController implements StorefrontBrandApiDocs {
    private final BrandService brandService;

    @GetMapping
    public List<StorefrontBrandResponse> getStorefrontBrands(@RequestParam(required = false) Integer categoryId) {
        return brandService.getStorefrontBrands(categoryId);
    }
}
