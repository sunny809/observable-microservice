package com.example.order.adapter.inbound.rest;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Servlet filter that extracts or generates a trace ID for distributed tracing.
 *
 * <p>Trace ID resolution priority:
 * <ol>
 *   <li>{@code X-B3-TraceId} header (Zipkin B3 propagation)</li>
 *   <li>{@code traceparent} header (W3C trace context)</li>
 *   <li>Random UUID (fallback for new traces)</li>
 * </ol>
 *
 * <p>The resolved trace ID is stored in SLF4J's MDC under the key {@code traceId}
 * and returned to the client in the {@code X-Trace-Id} response header.
 */
@Component
public class TraceFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String traceId = resolveTraceId(httpRequest);
        MDC.put("traceId", traceId);
        httpResponse.setHeader("X-Trace-Id", traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * Resolves the trace ID from the request headers.
     *
     * <p>Priority order:
     * <ol>
     *   <li>{@code X-B3-TraceId} (Zipkin B3)</li>
     *   <li>{@code traceparent} (W3C)</li>
     *   <li>Random UUID (new trace)</li>
     * </ol>
     *
     * @param request the HTTP request
     * @return the resolved trace ID
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-B3-TraceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("traceparent");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }
}
