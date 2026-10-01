package com.xdpsx.ecommerce.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAddressRequest(
        @NotBlank @Size(max = 128) String recipientName,
        @NotBlank @Pattern(regexp = "\\+?[0-9]{8,15}") String phoneNumber,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 128) String wardCommune,
        @NotBlank @Size(max = 128) String district,
        @NotBlank @Size(max = 128) String provinceCity,
        @Size(max = 20) String postalCode) {
    public UserAddressRequest {
        recipientName = trim(recipientName);
        phoneNumber = trim(phoneNumber);
        addressLine = trim(addressLine);
        wardCommune = trim(wardCommune);
        district = trim(district);
        provinceCity = trim(provinceCity);
        postalCode = blankToNull(postalCode);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        String normalized = trim(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
