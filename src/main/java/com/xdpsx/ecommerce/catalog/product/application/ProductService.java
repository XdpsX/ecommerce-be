package com.xdpsx.ecommerce.catalog.product.application;

import java.util.List;
import java.util.Map;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

public interface ProductService {
    PageResponse<StorefrontProductSummaryResponse> getStorefrontProducts(StorefrontProductFilter filter);

    StorefrontProductDetailResponse getStorefrontProductBySlug(String slug);

    List<ProductOptionResponse> getStorefrontFilterOptions(Integer categoryId, Integer brandId);

    PageResponse<StorefrontProductSummaryResponse> getLatestStorefrontProducts(int pageNum, int pageSize);

    PageResponse<AdminProductSummaryResponse> getAdminProducts(AdminProductFilter filter);

    AdminProductDetailResponse getAdminProduct(Long id);

    AdminProductSummaryResponse createProduct(ProductCreateRequest request);

    AdminProductSummaryResponse updateProduct(Long id, ProductUpdateRequest request);

    void deleteProduct(Long id);

    AdminProductDetailResponse updatePublication(Long id, UpdateProductPublicationRequest request);

    Map<String, Boolean> getSlugAvailability(String slug);
}
