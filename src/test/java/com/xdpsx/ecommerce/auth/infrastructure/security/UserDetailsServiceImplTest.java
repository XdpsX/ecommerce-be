package com.xdpsx.ecommerce.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @Test
    void loadUserByUsername_ShouldResolveCanonicalLocalEmail() {
        User user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .password("encoded")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
        when(userRepository.findByEmailAndAuthProvider("alice@example.com", AuthProvider.LOCAL))
                .thenReturn(Optional.of(user));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(userRepository);

        assertThat(service.loadUserByUsername("  Alice@Example.COM ")).isInstanceOf(CustomUserDetails.class);
        verify(userRepository).findByEmailAndAuthProvider("alice@example.com", AuthProvider.LOCAL);
    }

    @Test
    void daoAuthenticationProvider_ShouldHideNonLocalAccountAsBadCredentials() {
        when(userRepository.findByEmailAndAuthProvider("google@example.com", AuthProvider.LOCAL))
                .thenReturn(Optional.empty());
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new UserDetailsServiceImpl(userRepository));
        provider.setPasswordEncoder(new BCryptPasswordEncoder());

        assertThatThrownBy(() -> provider.authenticate(
                        new UsernamePasswordAuthenticationToken(" Google@Example.COM ", "password123")))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository).findByEmailAndAuthProvider("google@example.com", AuthProvider.LOCAL);
    }
}
