package com.xdpsx.ecommerce.catalog.product.application;

import java.util.List;
import java.util.Map;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

public interface ProductService {
    PageResponse<ProductResponse> filterAllProducts(ProductParams params);

    ProductDetailsDTO getProductById(Long id);

    ProductDetailsDTO getProductBySlug(String slug);

    ProductResponse createProduct(ProductCreateRequest request);

    ProductResponse updateProduct(Long id, ProductUpdateRequest request);

    void deleteProduct(Long id);

    void publishProduct(Long id, boolean status);

    Map<String, Boolean> checkExistsProduct(String slug);

    PageResponse<ProductResponse> getDiscountProducts(int pageNum, int pageSize);

    PageResponse<ProductResponse> getLatestProducts(int pageNum, int pageSize);

    PageResponse<ProductResponse> getProductsByCategoryId(
            Integer categoryId,
            int pageNum,
            int pageSize,
            List<Integer> brandIds,
            String sort,
            Double minPrice,
            Double maxPrice);
}
