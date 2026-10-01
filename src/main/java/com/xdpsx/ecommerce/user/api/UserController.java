package com.xdpsx.ecommerce.user.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.user.api.dto.UpdateUserProfileRequest;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.application.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;

    @GetMapping("/users/me")
    public ResponseEntity<UserProfile> getCurrentUser(Authentication authentication) {
        UserProfile profile = userService.getUserByEmail(authentication.getName());
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/users/me")
    public ResponseEntity<UserProfile> updateCurrentUser(
            Authentication authentication, @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userService.updateCurrentProfile(authentication.getName(), request);
        return ResponseEntity.ok(profile);
    }
}
