package com.xdpsx.ecommerce.common.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation id of the current HTTP request.
 *
 * <p>A client supplied {@value #HEADER_NAME} value is reused only when it matches {@link #VALID_ID}; a missing or
 * unsafe value is replaced by a freshly generated UUID, so header injection cannot forge or corrupt log output. The
 * resolved id is published in three places: a request attribute (the source error responses read from), the SLF4J
 * MDC (for logging), and the response header.
 *
 * <p>Runs at the highest precedence so the id is available to the whole chain, including Spring Security. The MDC
 * entry is always removed in a {@code finally} block because the MDC is thread local and request threads are pooled.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";

    /** Field name shared by the MDC entry and the RFC 9457 problem extension property. */
    public static final String CORRELATION_ID_KEY = "correlationId";

    /** Request attribute holding the resolved id, read back by the error boundaries. */
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER_NAME));
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        MDC.put(CORRELATION_ID_KEY, correlationId);
        // Set before the chain runs: the chain may commit the response before returning.
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    /**
     * Adds the correlation id established by this filter to a problem response. Does nothing when the filter did not
     * run for the request, for example when an error escapes the filter chain itself.
     */
    public static void applyCorrelationId(ProblemDetail problem, HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof String correlationId) {
            problem.setProperty(CORRELATION_ID_KEY, correlationId);
        }
    }

    private static String resolve(String candidate) {
        return (candidate != null && VALID_ID.matcher(candidate).matches())
                ? candidate
                : UUID.randomUUID().toString();
    }
}
