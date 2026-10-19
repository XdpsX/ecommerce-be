package com.xdpsx.ecommerce.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Metadata used to create an upload intent. File bytes are uploaded directly to the provider.")
public record CreateMediaUploadIntentRequest(
        @Schema(description = "Media use and transformation policy", example = "PRODUCT_IMAGE") @NotNull
        MediaPurpose purpose,

        @Schema(example = "front.png") @NotBlank @Size(max = 255)
        String filename,

        @Schema(example = "image/png") @NotBlank @Size(max = 100)
        String contentType,

        @Schema(description = "File size in bytes", example = "204800") @Positive
        long sizeBytes) {}
