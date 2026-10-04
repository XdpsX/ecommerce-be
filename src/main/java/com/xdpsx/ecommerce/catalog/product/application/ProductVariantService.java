package com.xdpsx.ecommerce.catalog.product.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;

public interface ProductVariantService {
    List<ProductVariantResponse> getVariants(Long productId);

    List<ProductVariantResponse> createVariants(Long productId, ProductVariantBatchCreateRequest request);

    ProductVariantResponse updateBarcode(Long productId, Long variantId, UpdateProductVariantBarcodeRequest request);

    ProductVariantResponse updatePrice(Long productId, Long variantId, UpdateProductVariantPriceRequest request);

    ProductVariantResponse updateStatus(Long productId, Long variantId, UpdateProductVariantStatusRequest request);

    ProductVariantResponse scheduleSale(Long productId, Long variantId, ScheduleProductVariantSaleRequest request);

    void removeSale(Long productId, Long variantId);
}
