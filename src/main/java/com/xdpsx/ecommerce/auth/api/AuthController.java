package com.xdpsx.ecommerce.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.auth.api.dto.LoginRequest;
import com.xdpsx.ecommerce.auth.api.dto.RegisterRequest;
import com.xdpsx.ecommerce.auth.api.dto.TokenResponse;
import com.xdpsx.ecommerce.auth.application.AuthService;
import com.xdpsx.ecommerce.auth.application.AuthenticatedSession;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;
    private final RefreshRequestGuard refreshRequestGuard;

    //    @Value("${app.oauth2.error-uri}")
    //    private String ERROR_URL;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authenticatedResponse(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticatedResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        requireSessionRequestHeader(request);
        return authenticatedResponse(authService.refresh(refreshCookieService.read(request)));
    }

    @PostMapping("/logout")
    @ApiResponse(responseCode = "204", description = "No Content")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        requireSessionRequestHeader(request);
        authService.logout(refreshCookieService.read(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.expire().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> authenticatedResponse(AuthenticatedSession session) {
        TokenResponse response =
                TokenResponse.builder().accessToken(session.accessToken()).build();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieService
                                .create(session.refreshCredential(), session.refreshExpiresAt())
                                .toString())
                .body(response);
    }

    private void requireSessionRequestHeader(HttpServletRequest request) {
        if (!refreshRequestGuard.isAllowed(request)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
    }

    //    @GetMapping("/nopage")
    //    public void nopageRedirect(HttpServletResponse response) throws IOException {
    //        response.sendRedirect(ERROR_URL);
    //    }
}
