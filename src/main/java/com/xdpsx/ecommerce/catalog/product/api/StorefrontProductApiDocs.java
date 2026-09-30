package com.xdpsx.ecommerce.catalog.product.api;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.pagination.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product API")
interface StorefrontProductApiDocs {
    @Operation(summary = "Search storefront products", description = "Retrieve only currently visible products.")
    PageResponse<StorefrontProductSummaryResponse> getProducts(@ParameterObject StorefrontProductFilter filter);

    @Operation(
            summary = "Get storefront product by slug",
            description = "Retrieve a visible product and its active variant selection matrix.",
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found or hidden",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    StorefrontProductDetailResponse getProductBySlug(
            @Parameter(description = "Product slug", example = "mechanical-keyboard") String slug);

    @Operation(summary = "Get storefront product filter options")
    List<ProductOptionResponse> getFilterOptions(Integer categoryId, Integer brandId);

    @Operation(summary = "Get latest storefront products")
    PageResponse<StorefrontProductSummaryResponse> getLatestProducts(int pageNum, int pageSize);
}
