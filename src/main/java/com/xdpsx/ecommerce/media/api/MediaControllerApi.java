package com.xdpsx.ecommerce.media.api;

import org.springframework.http.MediaType;

import com.xdpsx.ecommerce.common.error.ApiProblemSchema;
import com.xdpsx.ecommerce.common.error.ValidationProblemSchema;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Media API")
public interface MediaControllerApi {

    @Operation(
            summary = "Upload image",
            description = "Uploads an image file to the server. Max file size: 2MB",
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
    ViewMediaDTO createMedia(@Parameter(description = "category, brand,...") String resource, CreateMediaDTO request);

    @Operation(
            summary = "Delete media",
            description = "Deletes a media file from the server",
            responses = {
                @ApiResponse(responseCode = "204", description = "No Content", content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "Not Found",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal Server Error",
                        content = @Content(schema = @Schema(implementation = ApiProblemSchema.class)))
            })
    void deleteMedia(@Parameter(description = "ID of the media to be deleted") String id);
}
