package com.xdpsx.ecommerce.user.application;

import java.util.List;

import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;

public interface UserAddressService {
    List<UserAddressResponse> list(String principalEmail);

    UserAddressResponse create(String principalEmail, UserAddressRequest request);

    UserAddressResponse replace(String principalEmail, Long addressId, UserAddressRequest request);

    void delete(String principalEmail, Long addressId);
}
