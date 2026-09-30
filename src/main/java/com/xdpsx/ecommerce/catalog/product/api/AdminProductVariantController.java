package com.xdpsx.ecommerce.catalog.product.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.application.ProductVariantService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/products/{productId}/variants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductVariantController implements AdminProductVariantApiDocs {
    private final ProductVariantService productVariantService;

    @GetMapping
    public List<ProductVariantResponse> getVariants(@PathVariable Long productId) {
        return productVariantService.getVariants(productId);
    }

    @PostMapping
    public ResponseEntity<List<ProductVariantResponse>> createVariants(
            @PathVariable Long productId, @Valid @RequestBody ProductVariantBatchCreateRequest request) {
        List<ProductVariantResponse> data = productVariantService.createVariants(productId, request);
        return ResponseEntity.created(URI.create("/admin/products/" + productId + "/variants"))
                .body(data);
    }

    @PutMapping("/{variantId}/barcode")
    public ProductVariantResponse updateBarcode(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantBarcodeRequest request) {
        return productVariantService.updateBarcode(productId, variantId, request);
    }

    @PatchMapping("/{variantId}/price")
    public ProductVariantResponse updatePrice(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantPriceRequest request) {
        return productVariantService.updatePrice(productId, variantId, request);
    }

    @PatchMapping("/{variantId}/status")
    public ProductVariantResponse updateStatus(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantStatusRequest request) {
        return productVariantService.updateStatus(productId, variantId, request);
    }

    @PutMapping("/{variantId}/sale")
    public ProductVariantResponse scheduleSale(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody ScheduleProductVariantSaleRequest request) {
        return productVariantService.scheduleSale(productId, variantId, request);
    }

    @DeleteMapping("/{variantId}/sale")
    public ResponseEntity<Void> removeSale(@PathVariable Long productId, @PathVariable Long variantId) {
        productVariantService.removeSale(productId, variantId);
        return ResponseEntity.noContent().build();
    }
}
