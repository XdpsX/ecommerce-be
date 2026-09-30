package com.xdpsx.ecommerce.common.error;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only representation of the RFC 9457 problem response built by {@link ApiProblemFactory}. Used
 * exclusively for OpenAPI {@code @Schema} references; it is never instantiated at runtime.
 */
@Schema(name = "ApiProblem", description = "RFC 9457 problem response")
public record ApiProblemSchema(
        @Schema(description = "Problem type URI", example = "about:blank")
        String type,

        @Schema(description = "Controlled human-readable summary", example = "Resource not found")
        String title,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Controlled explanation", example = "The requested resource does not exist")
        String detail,

        @Schema(description = "Request path", example = "/admin/brands/123")
        String instance,

        @Schema(
                description = "Correlation id of the failing request, also returned in the X-Correlation-ID header",
                example = "680461dd-851b-4f52-86ea-506fac28ea65")
        String correlationId,

        @Schema(description = "Stable machine-readable error code", example = "RESOURCE_NOT_FOUND")
        String code,

        @Schema(description = "Client-safe contextual parameters", example = "{\"resourceType\":\"brand\"}")
        Map<String, Object> parameters) {}
