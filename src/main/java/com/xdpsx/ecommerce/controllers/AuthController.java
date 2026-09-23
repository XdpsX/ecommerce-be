package com.xdpsx.ecommerce.controllers;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.dtos.auth.LoginRequest;
import com.xdpsx.ecommerce.dtos.auth.RegisterRequest;
import com.xdpsx.ecommerce.dtos.auth.TokenResponse;
import com.xdpsx.ecommerce.services.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    //    @Value("${app.oauth2.error-uri}")
    //    private String ERROR_URL;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    //    @GetMapping("/nopage")
    //    public void nopageRedirect(HttpServletResponse response) throws IOException {
    //        response.sendRedirect(ERROR_URL);
    //    }
}
