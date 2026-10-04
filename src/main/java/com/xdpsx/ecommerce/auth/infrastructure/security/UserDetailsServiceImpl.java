package com.xdpsx.ecommerce.auth.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String canonicalEmail = EmailIdentity.canonicalize(username);
        User user = userRepository
                .findByEmailAndAuthProvider(canonicalEmail, AuthProvider.LOCAL)
                .orElseThrow(() ->
                        new UsernameNotFoundException(String.format("User with email=%s not found", canonicalEmail)));
        return CustomUserDetails.buildFromUser(user);
    }
}
