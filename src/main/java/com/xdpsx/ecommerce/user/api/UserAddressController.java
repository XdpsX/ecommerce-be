package com.xdpsx.ecommerce.user.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;
import com.xdpsx.ecommerce.user.application.UserAddressService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/me/addresses")
@RequiredArgsConstructor
public class UserAddressController {
    private final UserAddressService addressService;

    @GetMapping
    public ResponseEntity<List<UserAddressResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(addressService.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<UserAddressResponse> create(
            Authentication authentication, @Valid @RequestBody UserAddressRequest request) {
        return ResponseEntity.status(201).body(addressService.create(authentication.getName(), request));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<UserAddressResponse> replace(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody UserAddressRequest request) {
        return ResponseEntity.ok(addressService.replace(authentication.getName(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long addressId) {
        addressService.delete(authentication.getName(), addressId);
        return ResponseEntity.noContent().build();
    }
}
