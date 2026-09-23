package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.auth.LoginRequest;
import com.xdpsx.ecommerce.dtos.auth.RegisterRequest;
import com.xdpsx.ecommerce.dtos.auth.TokenResponse;

public interface AuthService {
    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);
}
