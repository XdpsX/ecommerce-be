package com.xdpsx.ecommerce.catalog.product.api;

import java.net.URI;
import java.util.Map;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.application.ProductService;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController implements AdminProductApiDocs {
    private final ProductService productService;

    @GetMapping
    public PageResponse<AdminProductSummaryResponse> getProducts(@Valid @ParameterObject AdminProductFilter filter) {
        return productService.getAdminProducts(filter);
    }

    @GetMapping("/{id}")
    public AdminProductDetailResponse getProduct(@PathVariable Long id) {
        return productService.getAdminProduct(id);
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<AdminProductSummaryResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        AdminProductSummaryResponse data = productService.createProduct(request);
        return ResponseEntity.created(URI.create("/admin/products/" + data.id()))
                .body(data);
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    public AdminProductSummaryResponse updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @PatchMapping(path = "/{id}/publication", consumes = "application/json")
    public AdminProductDetailResponse updatePublication(
            @PathVariable Long id, @Valid @RequestBody UpdateProductPublicationRequest request) {
        return productService.updatePublication(id, request);
    }

    @GetMapping("/slug-availability")
    public Map<String, Boolean> getSlugAvailability(@RequestParam String slug) {
        return productService.getSlugAvailability(slug);
    }
}
