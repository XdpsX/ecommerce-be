package com.xdpsx.ecommerce.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.api.dto.UserAddressRequest;
import com.xdpsx.ecommerce.user.api.dto.UserAddressResponse;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.domain.UserAddress;
import com.xdpsx.ecommerce.user.persistence.UserAddressRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceImplTest {
    private static final UserAddressRequest REQUEST = new UserAddressRequest(
            "  Alice  ", "+84901234567", "  1 Main Street  ", " Ward 1 ", " District 1 ", " City ", "  ");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAddressRepository addressRepository;

    @Test
    void create_ShouldAssignResolvedOwnerAndNormalizeFields() {
        User user = user(7L, "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.save(any(UserAddress.class))).thenAnswer(invocation -> {
            UserAddress address = invocation.getArgument(0);
            return address;
        });

        UserAddressResponse response = service().create(" Alice@Example.COM ", REQUEST);

        assertThat(response.recipientName()).isEqualTo("Alice");
        assertThat(response.addressLine()).isEqualTo("1 Main Street");
        assertThat(response.postalCode()).isNull();
        var captor = org.mockito.ArgumentCaptor.forClass(UserAddress.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void list_ShouldUseOwnerScopedQueryAndPreserveRepositoryOrder() {
        User user = user(7L, "alice@example.com");
        UserAddress first = address(user, 2L, "Second");
        UserAddress second = address(user, 5L, "Fifth");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findAllByUserIdOrderByIdAsc(7L)).thenReturn(List.of(first, second));

        List<UserAddressResponse> responses = service().list("alice@example.com");

        assertThat(responses).extracting(UserAddressResponse::id).containsExactly(2L, 5L);
        verify(addressRepository).findAllByUserIdOrderByIdAsc(7L);
    }

    @Test
    void replace_ShouldReplaceAllFieldsAndKeepOwner() {
        User owner = user(7L, "alice@example.com");
        UserAddress address = address(owner, 42L, "Old recipient");
        UserAddressRequest replacement = new UserAddressRequest(
                "  New recipient  ",
                "+84901112233",
                "  New street  ",
                " New ward ",
                " New district ",
                " New city ",
                "70000");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        when(addressRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.of(address));

        UserAddressResponse response = service().replace("alice@example.com", 42L, replacement);

        assertThat(response)
                .isEqualTo(new UserAddressResponse(
                        42L,
                        "New recipient",
                        "+84901112233",
                        "New street",
                        "New ward",
                        "New district",
                        "New city",
                        "70000"));
        assertThat(address.getUser()).isSameAs(owner);
        verify(addressRepository, never()).save(any(UserAddress.class));
    }

    @Test
    void replaceAndDelete_CrossOwnerMissShouldBeNondisclosingAndNotMutate() {
        User owner = user(7L, "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        when(addressRepository.findByIdAndUserId(42L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().replace("alice@example.com", 42L, REQUEST))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service().delete("alice@example.com", 42L))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(addressRepository, never()).save(any(UserAddress.class));
        verify(addressRepository, never()).delete(any(UserAddress.class));
    }

    private UserAddressServiceImpl service() {
        return new UserAddressServiceImpl(userRepository, addressRepository);
    }

    private static User user(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .name("Customer")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
    }

    private static UserAddress address(User user, Long id, String recipientName) {
        UserAddress address = UserAddress.builder().user(user).build();
        address.replaceDetails(recipientName, "+84901234567", "Street", "Ward", "District", "City", null);
        try {
            var field = UserAddress.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(address, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return address;
    }
}
