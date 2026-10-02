package com.xdpsx.ecommerce.checkout.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutResponse;
import com.xdpsx.ecommerce.checkout.application.CheckoutService;
import com.xdpsx.ecommerce.order.infrastructure.web.RequestUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkoutService;

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest httpServletRequest) {
        CheckoutResponse response = checkoutService.checkout(
                authentication.getName(), request, idempotencyKey, RequestUtil.getIpAddress(httpServletRequest));
        return ResponseEntity.status(response.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(response);
    }
}
