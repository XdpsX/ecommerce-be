package com.xdpsx.ecommerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ShippingAddressSnapshot {
    @Column(name = "recipient_name", length = 128)
    private String recipientName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "ward_commune", length = 128)
    private String wardCommune;

    @Column(name = "district", length = 128)
    private String district;

    @Column(name = "province_city", length = 128)
    private String provinceCity;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    public static ShippingAddressSnapshot from(com.xdpsx.ecommerce.user.domain.UserAddress address) {
        return new ShippingAddressSnapshot(
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getAddressLine(),
                address.getWardCommune(),
                address.getDistrict(),
                address.getProvinceCity(),
                address.getPostalCode());
    }

    public void validateComplete() {
        if (isBlank(recipientName)
                || isBlank(phoneNumber)
                || isBlank(addressLine)
                || isBlank(wardCommune)
                || isBlank(district)
                || isBlank(provinceCity)) {
            throw new IllegalArgumentException("shipping address snapshot must be complete");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
