package com.xdpsx.ecommerce.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginRequest {
    @NotBlank
    @Size(max = 64)
    @Email
    private String email;

    @NotBlank
    @Size(max = 255)
    private String password;
}
