package com.xdpsx.ecommerce.auth.application;

import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;
import com.xdpsx.ecommerce.auth.infrastructure.security.CustomUserDetails;
import com.xdpsx.ecommerce.auth.infrastructure.security.TokenProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Override
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_ALREADY_EXISTS, Map.of("resourceType", "user", "field", "email"));
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
        User savedUser = userRepository.save(user);
        String accessToken = tokenProvider.generateToken(savedUser);
        return TokenResponse.builder().accessToken(accessToken).build();
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        String accessToken = tokenProvider.generateToken(user);
        return TokenResponse.builder().accessToken(accessToken).build();
    }
}
