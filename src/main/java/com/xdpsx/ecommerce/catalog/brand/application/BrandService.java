package com.xdpsx.ecommerce.catalog.brand.application;

import com.xdpsx.ecommerce.catalog.brand.api.dto.*;
import com.xdpsx.ecommerce.catalog.shared.api.dto.CheckExistResponse;
import com.xdpsx.ecommerce.catalog.shared.api.dto.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

public interface BrandService {
    PageResponse<AdminBrandResponse> getAdminBrands(AdminBrandFilter filter);

    BrandDetailResponse getAdminBrandDetail(Integer id);

    BrandDetailResponse createBrand(CreateBrandRequest request);

    BrandDetailResponse updateBrand(Integer id, UpdateBrandRequest request);

    void deleteBrand(Integer id, ModifyExclusiveDTO request);

    CheckExistResponse checkBrandExist(BrandExistRequest request);
}
