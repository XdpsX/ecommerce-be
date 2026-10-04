package com.xdpsx.ecommerce.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "user_addresses", indexes = @Index(name = "ix_user_addresses_user_id", columnList = "user_id"))
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_addresses_user"))
    private User user;

    @Column(name = "recipient_name", length = 128, nullable = false)
    private String recipientName;

    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "address_line", length = 255, nullable = false)
    private String addressLine;

    @Column(name = "ward_commune", length = 128, nullable = false)
    private String wardCommune;

    @Column(name = "district", length = 128, nullable = false)
    private String district;

    @Column(name = "province_city", length = 128, nullable = false)
    private String provinceCity;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Builder
    private UserAddress(User user) {
        this.user = user;
    }

    public void replaceDetails(
            String recipientName,
            String phoneNumber,
            String addressLine,
            String wardCommune,
            String district,
            String provinceCity,
            String postalCode) {
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.addressLine = addressLine;
        this.wardCommune = wardCommune;
        this.district = district;
        this.provinceCity = provinceCity;
        this.postalCode = postalCode;
    }
}
