package com.xdpsx.ecommerce.cart.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;
import com.xdpsx.ecommerce.cart.application.CartMutationResult;
import com.xdpsx.ecommerce.cart.application.CartOwner;
import com.xdpsx.ecommerce.cart.application.CartOwnerResolver;
import com.xdpsx.ecommerce.cart.application.CartService;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    private final CartOwnerResolver ownerResolver;
    private final GuestCartCookieService cookieService;
    private final GuestCartRequestGuard requestGuard;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok(cartService.getCart(ownerResolver.resolve(authentication, request)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody CartItemRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        CartOwner owner = requireGuestGuard(authentication, httpRequest);
        return mutationResponse(cartService.addItem(owner, request));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> replaceItem(
            @PathVariable Long variantId,
            @Valid @RequestBody CartQuantityRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        CartOwner owner = requireGuestGuard(authentication, httpRequest);
        return mutationResponse(cartService.replaceItem(owner, variantId, request));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable Long variantId, Authentication authentication, HttpServletRequest httpRequest) {
        CartOwner owner = requireGuestGuard(authentication, httpRequest);
        return mutationResponse(cartService.removeItem(owner, variantId));
    }

    @PostMapping("/claim")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> claim(Authentication authentication, HttpServletRequest request) {
        return mutationResponse(cartService.claimGuestCart(authentication.getName(), cookieService.read(request)));
    }

    private CartOwner requireGuestGuard(Authentication authentication, HttpServletRequest request) {
        CartOwner owner = ownerResolver.resolve(authentication, request);
        if (!owner.isCustomer() && !requestGuard.isAllowed(request)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return owner;
    }

    private ResponseEntity<CartResponse> mutationResponse(CartMutationResult result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.expireGuestCookie()) {
            response.header(HttpHeaders.SET_COOKIE, cookieService.expire().toString());
        } else if (result.guestCredential() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookieService
                            .create(result.guestCredential(), result.guestExpiresAt())
                            .toString());
        }
        return response.body(result.response());
    }
}
