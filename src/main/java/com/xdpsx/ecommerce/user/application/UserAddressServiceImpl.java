package com.xdpsx.ecommerce.user.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.domain.UserAddress;
import com.xdpsx.ecommerce.user.persistence.UserAddressRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements UserAddressService {
    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserAddressResponse> list(String principalEmail) {
        User user = findUser(principalEmail);
        return addressRepository.findAllByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserAddressServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserAddressResponse create(String principalEmail, UserAddressRequest request) {
        User user = findUser(principalEmail);
        UserAddress address = UserAddress.builder().user(user).build();
        apply(address, request);
        return toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    public UserAddressResponse replace(String principalEmail, Long addressId, UserAddressRequest request) {
        User user = findUser(principalEmail);
        UserAddress address = findOwnedAddress(addressId, user.getId());
        apply(address, request);
        return toResponse(address);
    }

    @Override
    @Transactional
    public void delete(String principalEmail, Long addressId) {
        User user = findUser(principalEmail);
        UserAddress address = findOwnedAddress(addressId, user.getId());
        addressRepository.delete(address);
    }

    private User findUser(String email) {
        return userRepository
                .findByEmail(EmailIdentity.canonicalize(email))
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user")));
    }

    private UserAddress findOwnedAddress(Long addressId, Long userId) {
        return addressRepository
                .findByIdAndUserId(addressId, userId)
                .orElseThrow(() ->
                        new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user address")));
    }

    private static void apply(UserAddress address, UserAddressRequest request) {
        if (request == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("resourceType", "user address"));
        }
        address.replaceDetails(
                required(request.recipientName(), "recipientName", 128),
                phone(request.phoneNumber()),
                required(request.addressLine(), "addressLine", 255),
                required(request.wardCommune(), "wardCommune", 128),
                required(request.district(), "district", 128),
                required(request.provinceCity(), "provinceCity", 128),
                optional(request.postalCode(), 20));
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw validation(field);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validation(field);
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validation("postalCode");
        }
        return normalized;
    }

    private static String phone(String value) {
        String normalized = required(value, "phoneNumber", 20);
        if (!normalized.matches("\\+?[0-9]{8,15}")) {
            throw validation("phoneNumber");
        }
        return normalized;
    }

    private static ApplicationException validation(String field) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", field));
    }

    private static UserAddressResponse toResponse(UserAddress address) {
        return new UserAddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getAddressLine(),
                address.getWardCommune(),
                address.getDistrict(),
                address.getProvinceCity(),
                address.getPostalCode());
    }
}
