package com.xdpsx.ecommerce.catalog.brand.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Platform admin boundary for Brand. Every endpoint requires the {@code ADMIN} role.
 *
 * <p>The update and delete contracts carry the entity {@code version} as the optimistic-concurrency token; a stale
 * token is rejected with 409 before any association or media state changes.
 */
@RestController
@RequestMapping("/admin/brands")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBrandController implements AdminBrandApiDocs {
    private final BrandService brandService;

    @GetMapping
    public PageResponse<AdminBrandResponse> getAdminBrands(@ParameterObject @Valid AdminBrandFilter filter) {
        return brandService.getAdminBrands(filter);
    }

    @GetMapping("/{id}")
    public AdminBrandResponse getAdminBrand(@PathVariable Integer id) {
        return brandService.getAdminBrand(id);
    }

    @PostMapping
    public ResponseEntity<AdminBrandResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        AdminBrandResponse data = brandService.createBrand(request);
        return ResponseEntity.created(URI.create("/admin/brands/" + data.id())).body(data);
    }

    @PutMapping("/{id}")
    public AdminBrandResponse updateBrand(@PathVariable Integer id, @Valid @RequestBody UpdateBrandRequest request) {
        return brandService.updateBrand(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Integer id, @Valid @RequestBody DeleteBrandRequest request) {
        brandService.deleteBrand(id, request);
    }
}
