package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.brand.*;
import com.xdpsx.ecommerce.dtos.common.CheckExistResponse;
import com.xdpsx.ecommerce.dtos.common.ModifyExclusiveDTO;
import com.xdpsx.ecommerce.dtos.common.PageResponse;

public interface BrandService {
    PageResponse<AdminBrandResponse> getAdminBrands(AdminBrandFilter filter);

    BrandDetailResponse getAdminBrandDetail(Integer id);

    BrandDetailResponse createBrand(CreateBrandRequest request);

    BrandDetailResponse updateBrand(Integer id, UpdateBrandRequest request);

    void deleteBrand(Integer id, ModifyExclusiveDTO request);

    CheckExistResponse checkBrandExist(BrandExistRequest request);
}
