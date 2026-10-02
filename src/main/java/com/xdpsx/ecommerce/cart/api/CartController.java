package com.xdpsx.ecommerce.cart.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;
import com.xdpsx.ecommerce.cart.application.CartService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCartForCustomer(authentication.getName()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody CartItemRequest request, Authentication authentication) {
        return ResponseEntity.ok(cartService.addItem(authentication.getName(), request));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> replaceItem(
            @PathVariable Long variantId,
            @Valid @RequestBody CartQuantityRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(cartService.replaceItem(authentication.getName(), variantId, request));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable Long variantId, Authentication authentication) {
        return ResponseEntity.ok(cartService.removeItem(authentication.getName(), variantId));
    }
}
