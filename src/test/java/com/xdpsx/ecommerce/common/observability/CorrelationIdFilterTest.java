package com.xdpsx.ecommerce.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.xdpsx.ecommerce.common.error.ApiProblemFactory;
import com.xdpsx.ecommerce.common.error.ErrorCode;

/**
 * Protects the HTTP correlation id contract: a safe client supplied id is preserved, an unsafe or missing one is
 * replaced, and the resolved id reaches the request context, the MDC and the response header. The MDC is thread local,
 * so the test also guards that it is released when the request ends.
 */
class CorrelationIdFilterTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static final FilterChain NO_OP_CHAIN = (request, response) -> {};

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private MockHttpServletRequest requestWith(String incomingId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/brands");
        if (incomingId != null) {
            request.addHeader(CorrelationIdFilter.HEADER_NAME, incomingId);
        }
        return request;
    }

    /** Runs the filter, then reads the id back through the same accessor the error boundaries use. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain chain)
            throws Exception {
        filter.doFilter(request, response, chain);
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.INTERNAL_ERROR);
        CorrelationIdFilter.applyCorrelationId(problem, request);
        return (String) problem.getProperties().get(CorrelationIdFilter.CORRELATION_ID_KEY);
    }

    @Test
    @DisplayName("Keeps a valid client supplied id and publishes it to the header, MDC and request context")
    void doFilter_shouldReuseValidIncomingId() throws Exception {
        MockHttpServletRequest request = requestWith("client-supplied.id_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain capturingChain = (req, res) -> mdcDuringChain.set(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));

        String correlationId = run(request, response, capturingChain);

        assertThat(correlationId).isEqualTo("client-supplied.id_123");
        assertThat(mdcDuringChain.get()).isEqualTo("client-supplied.id_123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("client-supplied.id_123");
    }

    @Test
    @DisplayName("Generates a UUID when the header is absent and releases the MDC afterwards")
    void doFilter_shouldGenerateIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = requestWith(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String correlationId = run(request, response, NO_OP_CHAIN);

        assertThat(correlationId).matches(UUID_PATTERN);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(correlationId);
        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)).isNull();
    }

    @ParameterizedTest(name = "rejects unsafe incoming id [{0}]")
    @ValueSource(strings = {"contains space", "line\nbreak", "semi;colon", "<script>"})
    @DisplayName("Replaces an unsafe incoming id instead of echoing it into headers and logs")
    void doFilter_shouldReplaceUnsafeIncomingId(String incomingId) throws Exception {
        MockHttpServletRequest request = requestWith(incomingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String correlationId = run(request, response, NO_OP_CHAIN);

        assertThat(correlationId).matches(UUID_PATTERN).isNotEqualTo(incomingId);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Replaces an incoming id longer than the accepted maximum")
    void doFilter_shouldReplaceOverlongIncomingId() throws Exception {
        String overlongId = "a".repeat(129);
        MockHttpServletRequest request = requestWith(overlongId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String correlationId = run(request, response, NO_OP_CHAIN);

        assertThat(correlationId).matches(UUID_PATTERN).isNotEqualTo(overlongId);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Does not add a property when the filter did not run for the request")
    void applyCorrelationId_shouldDoNothingWithoutRequestContext() {
        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.INTERNAL_ERROR);

        CorrelationIdFilter.applyCorrelationId(problem, new MockHttpServletRequest("GET", "/brands"));

        assertThat(problem.getProperties()).doesNotContainKey(CorrelationIdFilter.CORRELATION_ID_KEY);
    }
}
