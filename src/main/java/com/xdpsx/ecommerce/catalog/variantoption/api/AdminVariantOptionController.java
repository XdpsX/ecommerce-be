package com.xdpsx.ecommerce.catalog.variantoption.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.variantoption.api.dto.*;
import com.xdpsx.ecommerce.catalog.variantoption.application.VariantOptionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/variant-options")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVariantOptionController implements AdminVariantOptionApiDocs {
    private final VariantOptionService variantOptionService;

    @GetMapping
    public List<VariantOptionResponse> getVariantOptions() {
        return variantOptionService.getVariantOptions();
    }

    @GetMapping("/{id}")
    public VariantOptionResponse getVariantOption(@PathVariable Long id) {
        return variantOptionService.getVariantOption(id);
    }

    @PostMapping
    public ResponseEntity<VariantOptionResponse> createVariantOption(
            @Valid @RequestBody CreateVariantOptionRequest request) {
        VariantOptionResponse data = variantOptionService.createVariantOption(request);
        return ResponseEntity.created(URI.create("/admin/variant-options/" + data.id()))
                .body(data);
    }

    @PutMapping("/{id}")
    public VariantOptionResponse updateVariantOption(
            @PathVariable Long id, @Valid @RequestBody UpdateVariantOptionRequest request) {
        return variantOptionService.updateVariantOption(id, request);
    }

    @PostMapping("/{optionId}/values")
    public ResponseEntity<VariantOptionValueResponse> addValue(
            @PathVariable Long optionId, @Valid @RequestBody CreateVariantOptionValueRequest request) {
        VariantOptionValueResponse data = variantOptionService.addValue(optionId, request);
        return ResponseEntity.created(URI.create("/admin/variant-options/" + optionId + "/values/" + data.id()))
                .body(data);
    }

    @PutMapping("/{optionId}/values/{valueId}")
    public VariantOptionValueResponse updateValue(
            @PathVariable Long optionId,
            @PathVariable Long valueId,
            @Valid @RequestBody UpdateVariantOptionValueRequest request) {
        return variantOptionService.updateValue(optionId, valueId, request);
    }

    @PutMapping("/{optionId}/values/order")
    public List<VariantOptionValueResponse> reorderValues(
            @PathVariable Long optionId, @Valid @RequestBody ReorderVariantOptionValuesRequest request) {
        return variantOptionService.reorderValues(optionId, request);
    }
}
