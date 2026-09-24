package com.xdpsx.ecommerce.catalog.brand.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BrandController {
    private final BrandService brandService;

    @GetMapping("/admin/brands")
    public PageResponse<AdminBrandResponse> getAdminBrands(@ParameterObject @Valid AdminBrandFilter filter) {
        return brandService.getAdminBrands(filter);
    }

    @GetMapping("/admin/brands/{id}")
    public BrandDetailResponse getAdminBrandDetail(@PathVariable Integer id) {
        return brandService.getAdminBrandDetail(id);
    }

    @PostMapping(path = "/brands/create")
    public ResponseEntity<BrandDetailResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        BrandDetailResponse data = brandService.createBrand(request);
        return ResponseEntity.created(URI.create("/admin/brands/" + data.id())).body(data);
    }

    @PutMapping("/brands/{id}/update")
    public BrandDetailResponse updateBrand(@PathVariable Integer id, @Valid @RequestBody UpdateBrandRequest request) {
        return brandService.updateBrand(id, request);
    }

    @DeleteMapping("/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Integer id, @Valid @RequestBody ModifyExclusiveDTO request) {
        brandService.deleteBrand(id, request);
    }

    @PostMapping("/brands/exists")
    public CheckExistResponse checkBrandExist(@Valid @RequestBody BrandExistRequest request) {
        return brandService.checkBrandExist(request);
    }
}
