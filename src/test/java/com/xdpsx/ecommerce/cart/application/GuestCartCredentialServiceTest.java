package com.xdpsx.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class GuestCartCredentialServiceTest {
    private final GuestCartCredentialService service = new GuestCartCredentialService();

    @Test
    void issueAndParse_ShouldRoundTripWithoutPersistingTheRawSecret() {
        GuestCartCredentialService.IssuedSecret issued = service.issueSecret();
        String credential = service.format(42L, issued.secret());

        GuestCartCredentialService.ParsedCredential parsed =
                service.parse(credential).orElseThrow();

        assertThat(parsed.cartId()).isEqualTo(42L);
        assertThat(parsed.secret()).isEqualTo(issued.secret());
        assertThat(issued.secretHash()).isNotEqualTo(issued.secret());
        assertThat(credential).doesNotContain(Base64.getEncoder().encodeToString(issued.secret()));
        assertThat(service.matches(issued.secretHash(), parsed.secret())).isTrue();
    }

    @Test
    void parse_ShouldRejectMalformedOrNonCanonicalCredentials() {
        assertThat(service.parse("42.not-a-base64url-secret")).isEmpty();
        assertThat(service.parse("42." + "A".repeat(43) + "=")).isEmpty();
        assertThat(service.parse("0." + "A".repeat(43))).isEmpty();
    }
}
