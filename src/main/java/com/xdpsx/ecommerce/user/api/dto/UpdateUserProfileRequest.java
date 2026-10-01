package com.xdpsx.ecommerce.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 64) String name) {
    public UpdateUserProfileRequest {
        name = name == null ? null : name.trim();
    }
}
