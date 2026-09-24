package com.xdpsx.ecommerce.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the controlled RFC 9457 problem responses shared by the MVC exception handler and the security components
 * that run outside Spring MVC (for example {@code AuthenticationEntryPoint}).
 *
 * <p>This is the single place that maps an {@link ErrorCode} to an HTTP status and adds the common extension
 * properties ({@code code}, {@code parameters}). It is intentionally not a registry, localization framework, or
 * general response builder.
 */
public final class ApiProblemFactory {

    private ApiProblemFactory() {}

    public static ProblemDetail create(ErrorCode code) {
        return create(code, Map.of());
    }

    public static ProblemDetail create(ErrorCode code, Map<String, Object> parameters) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusFor(code), code.detail());
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        if (parameters != null && !parameters.isEmpty()) {
            problem.setProperty("parameters", parameters);
        }
        return problem;
    }

    public static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED,
                    MALFORMED_REQUEST,
                    CART_EMPTY,
                    INVALID_CATEGORY_DEPTH,
                    INVALID_PRODUCT_IMAGE_COUNT,
                    INVALID_IMAGE_WIDTH -> HttpStatus.BAD_REQUEST;
            case INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RESOURCE_ALREADY_EXISTS, RESOURCE_IN_USE, CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
            case INVALID_MEDIA_RESOURCE_TYPE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case MEDIA_UPLOAD_FAILED -> HttpStatus.BAD_GATEWAY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
