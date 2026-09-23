package com.xdpsx.ecommerce.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.xdpsx.ecommerce.entities.User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenProvider {
    @Value("${app.jwt.secret}")
    private String SECRET_KEY;

    @Value("${app.jwt.expiration.seconds}")
    private Long EXPIRATION_SECONDS;

    public String generateToken(User user) {
        CustomUserDetails userDetails = CustomUserDetails.buildFromUser(user);
        return generateToken(userDetails);
    }

    public String generateToken(CustomUserDetails user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("xdpsx.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now()
                        .plus(EXPIRATION_SECONDS, ChronoUnit.SECONDS)
                        .toEpochMilli()))
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
            jwsObject.sign(new MACSigner(SECRET_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }
}
