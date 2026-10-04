package com.xdpsx.ecommerce.user.api.dto;

public record UserAddressResponse(
        Long id,
        String recipientName,
        String phoneNumber,
        String addressLine,
        String wardCommune,
        String district,
        String provinceCity,
        String postalCode) {}
