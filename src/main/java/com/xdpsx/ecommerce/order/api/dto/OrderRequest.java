package com.xdpsx.ecommerce.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class OrderRequest {
    @NotBlank
    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 20)
    private String mobileNumber;

    @Size(max = 500)
    private String description;

    private String ipAddress;
}
