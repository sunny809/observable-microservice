package io.o11y.kit.webmvc;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.http.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link HandlerInterceptor} that records server-side HTTP request metrics.
 *
 * <p>In {@link #preHandle}, stores the start time and resolves the trace ID.
 * In {@link #afterCompletion}, computes the round-trip duration and records
 * it via {@link HttpMetricRecorder#recordServerRequest}.
 *
 * <p>Register this interceptor with Spring MVC to automatically observe
 * all Controller requests without modifying controller code.
 *
 * <p>Trace ID resolution:
 * <ol>
 *   <li>{@code X-B3-TraceId} header (Zipkin B3)</li>
 *   <li>{@code traceparent} header (W3C)</li>
 *   <li>Random UUID fallback</li>
 * </ol>
 * The resolved trace ID is set in MDC under {@code traceId} and returned
 * in the {@code X-Trace-Id} response header.
 *
 * @since 0.1.0
 */
public class ServerObservationHandler implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ServerObservationHandler.class);
    private static final String START_TIME_ATTR = ServerObservationHandler.class.getName() + ".startTime";
    private static final String TRACE_ID_ATTR = ServerObservationHandler.class.getName() + ".traceId";
    private static final String UNKNOWN_URI = "UNKNOWN";

    private final HttpMetricRecorder recorder;

    public ServerObservationHandler(HttpMetricRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Resolve trace ID from headers
        String traceId = TraceIdResolver.resolve(name -> request.getHeader(name));
        request.setAttribute(TRACE_ID_ATTR, traceId);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        // Record start time
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            if (startTime != null) {
                long durationMs = System.currentTimeMillis() - startTime;
                String method = request.getMethod();
                String uri = extractPattern(request);
                int status = response.getStatus();

                recorder.recordServerRequest(method, uri, status, durationMs);
            }
        } catch (Exception e) {
            log.warn("Failed to record server request metrics", e);
        } finally {
            // Ensure MDC is always cleaned up, even if recordServerRequest throws
            MDC.remove("traceId");
        }
    }

    /**
     * Extracts the URI pattern (e.g., "/api/v1/orders/{id}") rather than
     * the actual path, to avoid high-cardinality metric tags.
     */
    private String extractPattern(HttpServletRequest request) {
        Object bestPattern = request.getAttribute(
                "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
        if (bestPattern instanceof String pattern) {
            return pattern;
        }
        return UNKNOWN_URI;
    }
}
