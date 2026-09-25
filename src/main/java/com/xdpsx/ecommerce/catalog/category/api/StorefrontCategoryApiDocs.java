package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeFilter;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentation-only interface for the public Category reads.
 *
 * <p>The tree currently filters on the stored {@code ACTIVE} status of each node; a node stored as active under
 * an inactive parent is still returned. Correct effective-status filtering is deferred to the storefront read
 * model work.
 */
@Tag(name = "Category API")
interface StorefrontCategoryApiDocs {
    @Operation(
            summary = "Get category tree",
            description = "Retrieve the category hierarchy. Only categories stored as ACTIVE are returned.",
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ValidationProblemSchema.class)))
            })
    List<CategoryTreeResponse> getCategoryTree(CategoryTreeFilter filter);
}
