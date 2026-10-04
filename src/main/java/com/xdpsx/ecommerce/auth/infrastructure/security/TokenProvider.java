package com.xdpsx.ecommerce.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.xdpsx.ecommerce.config.AuthSessionProperties;
import com.xdpsx.ecommerce.user.domain.User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenProvider {
    private final AuthSessionProperties authSessionProperties;
    private final java.time.Clock clock;

    @Value("${app.jwt.secret}")
    private String SECRET_KEY;

    @Value("${app.jwt.expiration.seconds}")
    private Long EXPIRATION_SECONDS;

    @Autowired
    public TokenProvider(AuthSessionProperties authSessionProperties, java.time.Clock clock) {
        this.authSessionProperties = authSessionProperties;
        this.clock = clock;
    }

    public TokenProvider() {
        this(new AuthSessionProperties(), java.time.Clock.systemUTC());
    }

    public String generateToken(User user) {
        CustomUserDetails userDetails = CustomUserDetails.buildFromUser(user);
        return generateToken(userDetails);
    }

    public String generateToken(CustomUserDetails user) {
        return generateToken(user, java.time.Duration.ofSeconds(EXPIRATION_SECONDS));
    }

    public String generateLocalToken(User user) {
        return generateLocalToken(CustomUserDetails.buildFromUser(user));
    }

    public String generateLocalToken(CustomUserDetails user) {
        return generateToken(user, authSessionProperties.getLocalAccessTokenLifetime());
    }

    private String generateToken(CustomUserDetails user, java.time.Duration lifetime) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer(authSessionProperties.getExpectedIssuer())
                .issueTime(Date.from(clock.instant()))
                .expirationTime(Date.from(clock.instant().plus(lifetime)))
                .claim(
                        "scope",
                        user.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList()
                                .get(0))
                .build();
        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SECRET_KEY.getBytes(StandardCharsets.UTF_8)));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }
}
