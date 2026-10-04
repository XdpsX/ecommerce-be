package com.xdpsx.ecommerce.auth.application;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;

public interface AuthService {
    AuthenticatedSession register(RegisterRequest request);

    AuthenticatedSession login(LoginRequest request);

    AuthenticatedSession refresh(String refreshCredential);

    void logout(String refreshCredential);
}
