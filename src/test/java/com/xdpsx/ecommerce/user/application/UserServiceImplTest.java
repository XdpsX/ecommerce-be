package com.xdpsx.ecommerce.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.user.api.dto.UpdateUserProfileRequest;
import com.xdpsx.ecommerce.user.api.dto.UserProfile;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Test
    void getUserByEmail_ShouldCanonicalizePreMigrationIdentity() {
        User user = User.builder().id(1L).email("alice@example.com").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userMapper.fromEntityToProfile(user))
                .thenReturn(UserProfile.builder().email(user.getEmail()).build());

        new UserServiceImpl(userRepository, userMapper).getUserByEmail(" Alice@Example.COM ");

        verify(userRepository).findByEmail("alice@example.com");
    }

    @Test
    void updateCurrentProfile_ShouldAllowLocalDisplayNameOverrideForAnyProvider() {
        User user = User.builder()
                .id(1L)
                .name("Old name")
                .email("alice@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .role(Role.USER)
                .build();
        UserProfile profile = UserProfile.builder().id(1L).name("New name").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(userMapper.fromEntityToProfile(user)).thenReturn(profile);

        UserProfile result = new UserServiceImpl(userRepository, userMapper)
                .updateCurrentProfile(" Alice@Example.COM ", new UpdateUserProfileRequest("  New name  "));

        assertThat(result).isSameAs(profile);
        assertThat(user.getName()).isEqualTo("New name");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        verify(userRepository).findByEmail("alice@example.com");
    }
}
