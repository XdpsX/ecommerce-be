package com.xdpsx.ecommerce.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public record CreateMediaUploadIntentRequest(
        @NotNull MediaPurpose purpose,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long sizeBytes) {}
