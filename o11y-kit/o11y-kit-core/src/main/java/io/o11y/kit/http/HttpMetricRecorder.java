package io.o11y.kit.http;

/**
 * Records HTTP request metrics for both server-side and client-side calls.
 *
 * <p>This is the core abstraction for capturing round-trip latency and
 * HTTP status code distribution. Implementations can route these metrics
 * to Micrometer, OpenTelemetry, or any other observability backend.
 *
 * <p>Server-side calls represent inbound requests handled by a Controller.
 * Client-side calls represent outbound requests made via HTTP clients.
 *
 * <p>Thread-safety: implementations must be thread-safe.
 *
 * @since 0.1.0
 */
public interface HttpMetricRecorder {

    /**
     * Records a completed server-side (inbound) HTTP request.
     *
     * @param method     HTTP method (GET, POST, etc.)
     * @param uri        request URI pattern (e.g., "/api/v1/orders")
     * @param statusCode HTTP response status code
     * @param durationMs round-trip duration in milliseconds
     */
    void recordServerRequest(String method, String uri, int statusCode, long durationMs);

    /**
     * Records a completed client-side (outbound) HTTP request.
     *
     * @param method     HTTP method (GET, POST, etc.)
     * @param host       target host (e.g., "inventory-service:8081")
     * @param statusCode HTTP response status code
     * @param durationMs round-trip duration in milliseconds
     */
    void recordClientRequest(String method, String host, int statusCode, long durationMs);

    /**
     * Records a failed client-side request (connection error, timeout, etc.).
     *
     * @param method     HTTP method
     * @param host       target host
     * @param errorClass simple name of the exception class
     * @param durationMs time elapsed before failure in milliseconds
     */
    void recordClientError(String method, String host, String errorClass, long durationMs);
}
