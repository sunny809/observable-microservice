package io.o11y.kit.http;

import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves a trace ID from incoming HTTP headers or generates a new one.
 *
 * <p>Resolution priority:
 * <ol>
 *   <li>{@code X-B3-TraceId} (Zipkin B3 propagation)</li>
 *   <li>{@code traceparent} (W3C trace context, requires valid 32-char hex trace ID)</li>
 *   <li>Random UUID (fallback for new traces)</li>
 * </ol>
 *
 * <p>This utility is framework-agnostic and can be reused in both
 * Servlet filters and reactive WebClient filters.
 *
 * @since 0.1.0
 */
public final class TraceIdResolver {

    private static final Pattern HEX_32 = Pattern.compile("[0-9a-fA-F]{32}");
    private static final int W3C_PARTS = 4;

    private TraceIdResolver() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Resolves a trace ID from the provided header accessor.
     *
     * @param headerProvider function to get a header value by name (may return null)
     * @return a non-blank trace ID, never null
     */
    public static String resolve(Function<String, String> headerProvider) {
        String traceId = headerProvider.apply("X-B3-TraceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        traceId = headerProvider.apply("traceparent");
        if (traceId != null && !traceId.isBlank()) {
            String extracted = extractW3CTraceId(traceId);
            if (extracted != null) {
                return extracted;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts the trace ID from a W3C traceparent header value.
     *
     * <p>Format: {@code 00-traceId-spanId-01} where traceId is exactly 32 hex chars.
     *
     * @param traceparent the raw traceparent header value
     * @return the trace ID if valid, null otherwise
     */
    private static String extractW3CTraceId(String traceparent) {
        String[] parts = traceparent.split("-");
        if (parts.length != W3C_PARTS) {
            return null;
        }
        String traceId = parts[1];
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        if (!HEX_32.matcher(traceId).matches()) {
            return null;
        }
        return traceId;
    }
}
