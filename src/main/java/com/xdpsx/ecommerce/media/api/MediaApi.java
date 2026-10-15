package com.xdpsx.ecommerce.media.api;

import org.springframework.http.MediaType;

import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaUploadIntentRequest;
import com.xdpsx.ecommerce.media.api.dto.MediaDirectStatusResponse;
import com.xdpsx.ecommerce.media.api.dto.MediaUploadIntentResponse;
import com.xdpsx.ecommerce.media.api.dto.UploadedMediaDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Media API")
public interface MediaApi {

    @Operation(
            summary = "Create a signed media upload intent",
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Upload authorization created",
                        content = @Content(schema = @Schema(implementation = MediaUploadIntentResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class)))
            })
    MediaUploadIntentResponse createUploadIntent(CreateMediaUploadIntentRequest request);

    @Operation(
            summary = "Verify and complete a direct media upload",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Verified media status",
                        content = @Content(schema = @Schema(implementation = MediaDirectStatusResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Media not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Upload is not completable",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Provider asset rejected",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "503",
                        description = "Provider unavailable",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    MediaDirectStatusResponse completeUpload(String id);

    @Operation(
            summary = "Get media upload and processing status",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Media status",
                        content = @Content(schema = @Schema(implementation = MediaDirectStatusResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Media not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    MediaDirectStatusResponse getMediaStatus(String id);

    @Operation(
            summary = "Upload image",
            description = "Uploads an image file and schedules Cloudinary eager variants. The original source URL "
                    + "remains in url; named variants are returned under variants. Max file size: 2MB. "
                    + "Supported resources: category, brand, product, product-description. The authenticated "
                    + "uploader is recorded as the owner of the temporary Media.",
            requestBody =
                    @RequestBody(
                            description = "containing the image file and its metadata",
                            required = true,
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = CreateMediaDTO.class))),
            responses = {
                @ApiResponse(responseCode = "201", description = "Created"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error / Invalid image width",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {ValidationProblemSchema.class, ApiProblemSchema.class
                                                        }))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Media resource type is invalid",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "502",
                        description = "Media provider upload failed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal Server Error",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    UploadedMediaDTO createMedia(
            @Parameter(description = "category, brand, product, product-description") String resource,
            CreateMediaDTO request);

    @Operation(
            summary = "Delete media",
            description = "Claims the caller's pending signed intent or temporary Media for later cleanup. Only the "
                    + "authenticated uploader can delete it; active Media and another user's Media are not deletable.",
            responses = {
                @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Active Media cannot be deleted independently",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal Server Error",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    void deleteMedia(@Parameter(description = "ID of the media to be deleted") String id);
}
