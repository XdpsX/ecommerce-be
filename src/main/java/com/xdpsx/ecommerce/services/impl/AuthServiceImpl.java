package com.xdpsx.ecommerce.services.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.dtos.auth.LoginRequest;
import com.xdpsx.ecommerce.dtos.auth.RegisterRequest;
import com.xdpsx.ecommerce.dtos.auth.TokenResponse;
import com.xdpsx.ecommerce.entities.User;
import com.xdpsx.ecommerce.entities.enums.AuthProvider;
import com.xdpsx.ecommerce.entities.enums.Role;
import com.xdpsx.ecommerce.exceptions.DuplicateException;
import com.xdpsx.ecommerce.repositories.UserRepository;
import com.xdpsx.ecommerce.security.CustomUserDetails;
import com.xdpsx.ecommerce.security.TokenProvider;
import com.xdpsx.ecommerce.services.AuthService;

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
            throw new DuplicateException("Email %s is already in use".formatted(request.getEmail()));
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
