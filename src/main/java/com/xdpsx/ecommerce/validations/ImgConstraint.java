package com.xdpsx.ecommerce.validations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {ImgValidator.class})
public @interface ImgConstraint {
    String message() default "Invalid image";

    int minWidth();

    int maxNumber() default 1;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
