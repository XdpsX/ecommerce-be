package com.xdpsx.ecommerce.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthSessionPropertiesTest {
    private static Validator validator;
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void defaults_ShouldBeValid() {
        assertThat(validator.validate(new AuthSessionProperties())).isEmpty();
    }

    @Test
    void invalidValues_ShouldBeRejectedByBeanValidation() {
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setLocalAccessTokenLifetime(Duration.ZERO);
        properties.setRefreshSessionLifetime(Duration.ofSeconds(-1));
        properties.setRefreshCookieName(" ");
        properties.setRefreshCookieSameSite("Invalid");
        properties.setGuardHeaderName("");

        Set<ConstraintViolation<AuthSessionProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "localAccessTokenLifetimePositive",
                        "refreshSessionLifetimePositive",
                        "refreshCookieName",
                        "refreshCookieSameSite",
                        "guardHeaderName");
    }
}
