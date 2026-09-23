package com.xdpsx.ecommerce.auth.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;
import com.xdpsx.ecommerce.auth.application.AuthService;

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
