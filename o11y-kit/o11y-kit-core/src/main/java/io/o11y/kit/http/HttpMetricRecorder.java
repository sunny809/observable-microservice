package io.o11y.kit.http;

/**
 * Abstract base for recording HTTP request metrics for both server-side
 * and client-side calls. All methods have empty default implementations
 * so subclasses only need to override the methods they care about.
 *
 * <p>Server-side calls represent inbound requests handled by a Controller.
 * Client-side calls represent outbound requests made via HTTP clients.
 *
 * @since 0.1.0
 */
public abstract class HttpMetricRecorder {

    /**
     * Records a completed server-side (inbound) HTTP request.
     */
    public void recordServerRequest(String method, String uri, int statusCode, long durationMs) {}

    /**
     * Records a completed client-side (outbound) HTTP request.
     */
    public void recordClientRequest(String method, String host, int statusCode, long durationMs) {}

    /**
     * Records a failed client-side request (connection error, timeout, etc.).
     */
    public void recordClientError(String method, String host, String errorClass, long durationMs) {}
}
