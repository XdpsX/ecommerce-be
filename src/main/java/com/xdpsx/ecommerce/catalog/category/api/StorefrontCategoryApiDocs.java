package com.xdpsx.ecommerce.catalog.category.api;

import java.util.List;

import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.common.error.ApiProblemSchema;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentation-only interface for the public Category reads.
 *
 * <p>
 * Every read returns only effectively active categories: a node stored
 * {@code ACTIVE} under an inactive
 * ancestor is hidden together with its whole subtree. Siblings are always
 * ordered by
 * {@code displayOrder ASC, id ASC}; clients cannot change the order.
 */
@Tag(name = "Category API")
interface StorefrontCategoryApiDocs {

    @Operation(
            summary = "Get root categories",
            description = "Retrieve the effectively active top-level categories, ordered by display order. Hidden "
                    + "categories (stored inactive or under an inactive ancestor) are not returned.",
            responses = {@ApiResponse(responseCode = "200", description = "OK")})
    List<StorefrontCategoryResponse> getRootCategories();

    @Operation(
            summary = "Get category tree",
            description =
                    "Retrieve the full hierarchy of effectively active categories. Every sibling group is ordered "
                            + "by display order.",
            responses = {@ApiResponse(responseCode = "200", description = "OK")})
    List<CategoryTreeResponse> getCategoryTree();

    @Operation(
            summary = "Get category by slug",
            description = "Retrieve one effectively active category by its slug. Missing and hidden categories both "
                    + "return 404 so the lifecycle state is not leaked.",
            responses = {
                @ApiResponse(responseCode = "200", description = "OK"),
                @ApiResponse(
                        responseCode = "404",
                        description = "Category not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    StorefrontCategoryResponse getCategoryBySlug(
            @Parameter(description = "Category slug", example = "laptops") String slug);
}
