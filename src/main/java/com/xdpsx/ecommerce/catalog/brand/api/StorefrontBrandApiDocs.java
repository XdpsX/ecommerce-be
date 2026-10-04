package com.xdpsx.ecommerce.catalog.brand.api;

import java.util.List;

import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentation-only interface for the public Brand read model. */
@Tag(name = "Brand API")
interface StorefrontBrandApiDocs {
    @Operation(
            summary = "Get storefront brands",
            description = "Retrieve active brands in stable name/id order. When categoryId is supplied, only brands "
                    + "directly associated with that effectively active Category are returned. Missing or hidden "
                    + "Categories return 404.",
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Category not found or hidden",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    List<StorefrontBrandResponse> getStorefrontBrands(
            @Parameter(description = "Optional effectively active Category ID", example = "12") Integer categoryId);
}
