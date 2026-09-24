package com.xdpsx.ecommerce.common.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only representation of the validation problem response (code {@code VALIDATION_FAILED}). Used
 * exclusively for OpenAPI {@code @Schema} references; it is never instantiated at runtime.
 */
@Schema(name = "ValidationProblem", description = "RFC 9457 problem response for request validation failures")
public record ValidationProblemSchema(
        @Schema(description = "Problem type URI", example = "about:blank")
        String type,

        @Schema(description = "Controlled human-readable summary", example = "Request validation failed")
        String title,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Controlled explanation", example = "One or more request fields are invalid")
        String detail,

        @Schema(description = "Request path", example = "/auth/register")
        String instance,

        @Schema(description = "Stable machine-readable error code", example = "VALIDATION_FAILED")
        String code,

        @Schema(description = "Individual validation violations")
        List<FieldViolation> errors) {}
