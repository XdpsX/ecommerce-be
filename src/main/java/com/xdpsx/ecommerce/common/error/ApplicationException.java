package com.xdpsx.ecommerce.common.error;

import java.util.Map;

import lombok.Getter;

/**
 * The single application exception type. Carries a stable {@link ErrorCode} plus optional client-safe scalar
 * parameters. It deliberately contains no HTTP status: application code expresses what failed, while the API
 * boundary decides how the failure is transported.
 */
@Getter
public class ApplicationException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> parameters;

    public ApplicationException(ErrorCode code) {
        this(code, Map.of(), null);
    }

    public ApplicationException(ErrorCode code, Map<String, Object> parameters) {
        this(code, parameters, null);
    }

    public ApplicationException(ErrorCode code, Throwable cause) {
        this(code, Map.of(), cause);
    }

    public ApplicationException(ErrorCode code, Map<String, Object> parameters, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.parameters = Map.copyOf(parameters);
    }
}
