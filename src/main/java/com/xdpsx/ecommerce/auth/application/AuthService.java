package com.xdpsx.ecommerce.auth.application;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;

public interface AuthService {
    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
