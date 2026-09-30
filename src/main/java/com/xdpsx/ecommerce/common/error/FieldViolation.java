package com.xdpsx.ecommerce.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One individual request validation violation.
 *
 * <p>{@code field} is absent for object-level errors. Rejected values are intentionally not included because they can
 * contain passwords, tokens, or other sensitive input.
 */
public record FieldViolation(
        @JsonInclude(JsonInclude.Include.NON_NULL) String field, String code, String message) {}
