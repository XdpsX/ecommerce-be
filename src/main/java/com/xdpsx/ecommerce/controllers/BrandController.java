package com.xdpsx.ecommerce.controllers;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.constants.messages.SMessage;
import com.xdpsx.ecommerce.dtos.brand.*;
import com.xdpsx.ecommerce.dtos.common.APIResponse;
import com.xdpsx.ecommerce.dtos.common.CheckExistResponse;
import com.xdpsx.ecommerce.dtos.common.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.services.BrandService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BrandController {
    private final BrandService brandService;

    @GetMapping("/admin/brands")
    public APIResponse<PageResponse<AdminBrandResponse>> getAdminBrands(
            @ParameterObject @Valid AdminBrandFilter filter) {
        PageResponse<AdminBrandResponse> data = brandService.getAdminBrands(filter);
        return APIResponse.ok(data);
    }

    @GetMapping("/admin/brands/{id}")
    public APIResponse<BrandDetailResponse> getAdminBrandDetail(@PathVariable Integer id) {
        BrandDetailResponse data = brandService.getAdminBrandDetail(id);
        return APIResponse.ok(data);
    }

    @PostMapping(path = "/brands/create")
    @ResponseStatus(HttpStatus.CREATED)
    public APIResponse<BrandDetailResponse> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        BrandDetailResponse data = brandService.createBrand(request);
        return new APIResponse<>(HttpStatus.CREATED, data, SMessage.CREATE_SUCCESSFULLY);
    }

    @PutMapping("/brands/{id}/update")
    public APIResponse<BrandDetailResponse> updateBrand(
            @PathVariable Integer id, @Valid @RequestBody UpdateBrandRequest request) {
        BrandDetailResponse data = brandService.updateBrand(id, request);
        return APIResponse.ok(data);
    }

    @DeleteMapping("/{id}/delete")
    public APIResponse<Void> deleteBrand(@PathVariable Integer id, @Valid @RequestBody ModifyExclusiveDTO request) {
        brandService.deleteBrand(id, request);
        return APIResponse.noContent(SMessage.DELETE_SUCCESSFULLY);
    }

    @PostMapping("/brands/exists")
    public APIResponse<CheckExistResponse> checkBrandExist(@Valid @RequestBody BrandExistRequest request) {
        CheckExistResponse data = brandService.checkBrandExist(request);
        return APIResponse.ok(data);
    }
}
