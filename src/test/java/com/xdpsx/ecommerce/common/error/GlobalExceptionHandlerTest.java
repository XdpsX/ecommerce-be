package com.xdpsx.ecommerce.common.error;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;

import com.xdpsx.ecommerce.common.observability.CorrelationIdFilter;

/**
 * Protects the centralized error contract: stable {@code code}, controlled {@code title}/{@code detail}, mapped
 * status, safe {@code parameters}, {@code instance} derived from the request URI, and the {@code correlationId}
 * request context.
 */
class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "680461dd-851b-4f52-86ea-506fac28ea65";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ServletWebRequest request(String uri) {
        return request(uri, null);
    }

    private ServletWebRequest request(String uri, String correlationId) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", uri);
        servletRequest.setRequestURI(uri);
        if (correlationId != null) {
            servletRequest.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, correlationId);
        }
        return new ServletWebRequest(servletRequest);
    }

    @Test
    @DisplayName("ApplicationException maps to its status, code and safe parameters")
    void handleApplicationException_ShouldReturnMappedProblem() {
        ApplicationException ex = new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "brand", "resourceId", 123));

        ResponseEntity<Object> response = handler.handleApplicationException(ex, request("/admin/brands/123"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals("RESOURCE_NOT_FOUND", problem.getProperties().get("code"));
        assertEquals("Resource not found", problem.getTitle());
        assertEquals("/admin/brands/123", problem.getInstance().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters =
                (Map<String, Object>) problem.getProperties().get("parameters");
        assertEquals("brand", parameters.get("resourceType"));
        assertEquals(123, parameters.get("resourceId"));
    }

    @Test
    @DisplayName("Problem carries the correlationId established by the request filter")
    void handleApplicationException_ShouldIncludeCorrelationId() {
        ApplicationException ex = new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);

        ResponseEntity<Object> response =
                handler.handleApplicationException(ex, request("/admin/brands/123", CORRELATION_ID));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals(CORRELATION_ID, problem.getProperties().get(CorrelationIdFilter.CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("Problem omits correlationId when no request context was established")
    void handleApplicationException_ShouldOmitCorrelationIdWithoutRequestContext() {
        ApplicationException ex = new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);

        ResponseEntity<Object> response = handler.handleApplicationException(ex, request("/admin/brands/123"));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertFalse(problem.getProperties().containsKey(CorrelationIdFilter.CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("Framework client errors stay controlled 4xx instead of falling through to 500")
    void handleException_ShouldReturnControlledClientError_ForFrameworkException() throws Exception {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<Object> response = handler.handleException(ex, request("/brands", CORRELATION_ID));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals("MALFORMED_REQUEST", problem.getProperties().get("code"));
        assertEquals(ErrorCode.MALFORMED_REQUEST.detail(), problem.getDetail());
        assertEquals("/brands", problem.getInstance().toString());
        assertEquals(CORRELATION_ID, problem.getProperties().get(CorrelationIdFilter.CORRELATION_ID_KEY));
    }

    @Test
    @DisplayName("Unexpected exception returns 500 INTERNAL_ERROR without leaking the original message")
    void handleUnexpectedException_ShouldNotExposeOriginalMessage() {
        ResponseEntity<Object> response =
                handler.handleUnexpectedException(new IllegalStateException("db password=hunter2"), request("/orders"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals("INTERNAL_ERROR", problem.getProperties().get("code"));
        assertFalse(problem.getDetail().contains("hunter2"));
        assertFalse(problem.getTitle().contains("hunter2"));
        assertEquals("/orders", problem.getInstance().toString());
    }
}
