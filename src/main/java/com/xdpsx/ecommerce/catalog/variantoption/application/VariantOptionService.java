package com.xdpsx.ecommerce.catalog.variantoption.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.variantoption.api.dto.*;

public interface VariantOptionService {
    List<VariantOptionResponse> getVariantOptions();

    VariantOptionResponse getVariantOption(Long id);

    VariantOptionResponse createVariantOption(CreateVariantOptionRequest request);

    VariantOptionResponse updateVariantOption(Long id, UpdateVariantOptionRequest request);

    VariantOptionValueResponse addValue(Long optionId, CreateVariantOptionValueRequest request);

    VariantOptionValueResponse updateValue(Long optionId, Long valueId, UpdateVariantOptionValueRequest request);

    List<VariantOptionValueResponse> reorderValues(Long optionId, ReorderVariantOptionValuesRequest request);
}
