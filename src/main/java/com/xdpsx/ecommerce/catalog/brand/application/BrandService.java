package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

public interface BrandService {
    PageResponse<AdminBrandResponse> getAdminBrands(AdminBrandFilter filter);

    List<StorefrontBrandResponse> getStorefrontBrands(Integer categoryId);

    AdminBrandResponse getAdminBrand(Integer id);

    AdminBrandResponse createBrand(CreateBrandRequest request);

    AdminBrandResponse updateBrand(Integer id, UpdateBrandRequest request);

    void deleteBrand(Integer id, DeleteBrandRequest request);
}
