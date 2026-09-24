package com.xdpsx.ecommerce.common.error;

/**
 * Stable, machine-readable application error identifiers.
 *
 * <p>This enum intentionally has no dependency on Spring or HTTP. It describes what failed in
 * application terms together with controlled, client-safe {@code title} and {@code detail} texts. The API boundary
 * ({@link ApiProblemFactory} / {@code GlobalExceptionHandler}) decides how each code is transported over HTTP.
 */
public enum ErrorCode {
    VALIDATION_FAILED("Request validation failed", "One or more request fields are invalid"),
    MALFORMED_REQUEST("Malformed request", "The request could not be understood or is missing required parts"),
    INVALID_CREDENTIALS("Invalid credentials", "Email or password is incorrect"),
    AUTHENTICATION_REQUIRED("Authentication required", "Authentication is required to access this resource"),
    ACCESS_DENIED("Access denied", "You do not have permission to perform this operation"),
    RESOURCE_NOT_FOUND("Resource not found", "The requested resource does not exist"),
    RESOURCE_ALREADY_EXISTS("Resource already exists", "A resource with the same identifying value already exists"),
    RESOURCE_IN_USE("Resource in use", "The resource is still referenced and cannot be deleted"),
    CONCURRENT_MODIFICATION("Concurrent modification", "The resource was modified after it was retrieved"),
    CART_EMPTY("Cart is empty", "The cart does not contain any item eligible for checkout"),
    INVALID_CATEGORY_DEPTH("Invalid category depth", "The category hierarchy exceeds the maximum allowed depth"),
    INVALID_PRODUCT_IMAGE_COUNT(
            "Invalid product image count", "The number of product images exceeds the maximum allowed"),
    INVALID_MEDIA_RESOURCE_TYPE(
            "Invalid media resource type", "The media resource type does not match the requested resource"),
    INVALID_IMAGE_WIDTH("Invalid image width", "The image width is below the required minimum"),
    MEDIA_UPLOAD_FAILED("Media upload failed", "The media provider failed to process the upload"),
    INTERNAL_ERROR("Internal server error", "An unexpected error occurred");

    private final String title;
    private final String detail;

    ErrorCode(String title, String detail) {
        this.title = title;
        this.detail = detail;
    }

    /**
     * Controlled, human-readable summary of the error class. Never derived from exception messages.
     */
    public String title() {
        return title;
    }

    /**
     * Controlled, human-readable explanation of the error class. Never derived from exception messages.
     */
    public String detail() {
        return detail;
    }
}
