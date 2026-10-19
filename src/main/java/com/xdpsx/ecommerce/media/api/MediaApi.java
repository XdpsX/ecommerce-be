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
            description = "Creates a persisted, uploader-owned Media record and short-lived Cloudinary upload "
                    + "instructions. Send the exact returned fields with the file directly to the returned URL; "
                    + "do not send backend credentials to Cloudinary. intentExpiresAt is the application intent "
                    + "lifetime; uploadAuthorizationExpiresAt is the provider signature lifetime. Both are UTC. "
                    + "After provider upload, call complete so the backend inspects the stored provider identity.",
            requestBody =
                    @RequestBody(
                            description =
                                    "File metadata and the Media purpose; this endpoint does not receive file bytes.",
                            required = true,
                            content =
                                    @Content(schema = @Schema(implementation = CreateMediaUploadIntentRequest.class))),
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Intent persisted and provider upload instructions created",
                        content = @Content(schema = @Schema(implementation = MediaUploadIntentResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Unsupported purpose or invalid upload metadata; validation errors return HTTP 400",
                        content = @Content(schema = @Schema(implementation = ValidationProblemSchema.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "ADMIN role required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
            })
    MediaUploadIntentResponse createUploadIntent(CreateMediaUploadIntentRequest request);

    @Operation(
            summary = "Verify and complete a direct media upload",
            description = "Inspects Cloudinary using the public ID persisted with this caller's intent; provider "
                    + "response data supplied by the client is not accepted as proof. A verified UPLOADED Media "
                    + "can be attached while processing is PROCESSING or FAILED; Catalog attachment guards still "
                    + "apply. READY is not required. Repeating complete is supported only while Media remains "
                    + "TEMPORARY; after attachment, use GET status. Cross-uploader access is reported as 404.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Provider-verified upload and current processing status",
                        content = @Content(schema = @Schema(implementation = MediaDirectStatusResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "ADMIN role required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Media not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "MEDIA_UPLOAD_CONFLICT or MEDIA_UPLOAD_EXPIRED; inspect current status and "
                                + "do not retry every 409",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "MEDIA_ASSET_INVALID; the signed upload was rejected",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "503",
                        description = "MEDIA_PROVIDER_UNAVAILABLE; a bounded retry of complete may be attempted "
                                + "on the same usable intent",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    MediaDirectStatusResponse completeUpload(String id);

    @Operation(
            summary = "Get media upload and processing status",
            description = "Returns lifecycle status for the authenticated uploader's Media. originalUrl may be "
                    + "null before a verified upload. variants is empty until uploadStatus is UPLOADED and "
                    + "processingStatus is READY. PROCESSING or FAILED does not prevent Catalog attachment after "
                    + "successful complete; attachment purpose, owner, and lifecycle checks still apply. ADMIN "
                    + "is required and another uploader receives 404.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Media status",
                        content = @Content(schema = @Schema(implementation = MediaDirectStatusResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "ADMIN role required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Media not found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    MediaDirectStatusResponse getMediaStatus(String id);

    @Operation(
            summary = "Upload image",
            description = "Compatibility multipart path for existing clients. New clients should use the signed "
                    + "direct upload intent and complete flow. This endpoint is retained and is not deprecated. "
                    + "Uploads an image file and schedules Cloudinary eager variants. The original source URL "
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
                        responseCode = "401",
                        description = "Authentication required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "ADMIN role required",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Media not found or belongs to another uploader",
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
