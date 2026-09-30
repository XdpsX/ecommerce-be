package com.xdpsx.ecommerce.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequest {
    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Size(max = 64)
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 255)
    private String password;

    @JsonSetter("email")
    public void setEmail(String email) {
        this.email = EmailIdentity.canonicalize(email);
    }
}
