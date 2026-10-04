package com.xdpsx.ecommerce.cart.application;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.cart.api.GuestCartCookieService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CartOwnerResolver {
    private final GuestCartCookieService cookieService;

    public CartOwner resolve(Authentication authentication, HttpServletRequest request) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return CartOwner.customer(authentication.getName());
        }
        return CartOwner.guest(cookieService.read(request));
    }
}
