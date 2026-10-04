package com.xdpsx.ecommerce.catalog.shared.api.validation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SortValidator implements ConstraintValidator<SortConstraint, String> {
    private Set<String> validFields;

    @Override
    public void initialize(SortConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        validFields = new HashSet<>(Arrays.asList(constraintAnnotation.fields()));
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String actualField = value;
        if (value.startsWith("-")) {
            actualField = value.substring(1);
        }

        if (!validFields.contains(actualField)) {
            String message = String.join(", ", validFields);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Invalid sort field. Accept: " + message)
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
