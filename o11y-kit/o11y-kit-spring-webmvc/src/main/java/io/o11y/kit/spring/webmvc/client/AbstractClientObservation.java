package io.o11y.kit.spring.webmvc.client;

import io.o11y.kit.http.HttpMetricRecorder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Abstract base for client-side HTTP observation in Spring's blocking HTTP clients.
 *
 * <p>Both {@code RestTemplateObservationInterceptor} and
 * {@code RestClientObservationInterceptor} delegate to this class to avoid
 * duplicating metric-recording and span-management logic.
 *
 * <p>The lifecycle is:
 * <ol>
 *   <li>{@link #begin(HttpRequest)} — record start time, extract method/host,
 *       optionally start an OTel span</li>
 *   <li>On success: {@link #end(ObservationContext, ClientHttpResponse)} —
 *       compute duration, record via {@link HttpMetricRecorder#recordClientRequest},
 *       end span if present</li>
 *   <li>On error: {@link #error(ObservationContext, Throwable)} —
 *       compute duration, record via {@link HttpMetricRecorder#recordClientError},
 *       record exception on span, end span</li>
 * </ol>
 *
 * <p>Subclasses must bridge from their specific interceptor API
 * (e.g., {@code ClientHttpRequestInterceptor} or
 * {@code RestClient.RequestInterceptor}) to this shared lifecycle.
 *
 * @since 0.2.0-alpha
 */
public abstract class AbstractClientObservation {

    private static final String UNKNOWN_TEMPLATE = "UNKNOWN";

    private final HttpMetricRecorder recorder;
    private final Tracer tracer;

    /**
     * Creates a new {@code AbstractClientObservation} without OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     */
    protected AbstractClientObservation(HttpMetricRecorder recorder) {
        this(recorder, null);
    }

    /**
     * Creates a new {@code AbstractClientObservation} with OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     * @param tracer   the OpenTelemetry tracer; if null, tracing is skipped
     */
    protected AbstractClientObservation(HttpMetricRecorder recorder, Tracer tracer) {
        if (recorder == null) {
            throw new IllegalArgumentException("HttpMetricRecorder must not be null");
        }
        this.recorder = recorder;
        this.tracer = tracer;
    }

    /**
     * Begins observation of an outbound HTTP request.
     *
     * <p>Records the start time, extracts the HTTP method and host from the
     * request, and optionally starts an OpenTelemetry span if a tracer was
     * provided at construction time.
     *
     * @param request the outbound HTTP request; must not be null
     * @return an {@link ObservationContext} holding all state needed for
     *         the subsequent {@link #end} or {@link #error} call
     */
    protected ObservationContext begin(HttpRequest request) {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod().name();
        String host = resolveHost(request);

        Span span = null;
        String traceId = null;
        if (tracer != null) {
            span = tracer.spanBuilder("HTTP " + method)
                    .setAttribute("http.method", method)
                    .setAttribute("http.url", request.getURI().toString())
                    .setAttribute("http.host", host)
                    .startSpan();
            traceId = span.getSpanContext().getTraceId();
        }

        return new ObservationContext(method, host, startTime, span, traceId);
    }

    /**
     * Ends observation of a successful outbound HTTP request.
     *
     * <p>Computes the round-trip duration and records it via
     * {@link HttpMetricRecorder#recordClientRequest}. If an OTel span
     * was started in {@link #begin}, its {@code http.status_code} attribute
     * is set and the span is ended.
     *
     * @param ctx      the observation context returned by {@link #begin}
     * @param response the HTTP response received; must not be null
     */
    protected void end(ObservationContext ctx, ClientHttpResponse response) {
        long durationMs = System.currentTimeMillis() - ctx.startTime();
        int statusCode = extractStatusCode(response);

        if (ctx.span() != null) {
            ctx.span().setAttribute("http.status_code", statusCode);
        }

        recorder.recordClientRequest(ctx.method(), ctx.host(), statusCode, durationMs);

        if (ctx.span() != null) {
            ctx.span().end();
        }
    }

    /**
     * Ends observation of a failed outbound HTTP request.
     *
     * <p>Computes the round-trip duration and records it via
     * {@link HttpMetricRecorder#recordClientError}. If an OTel span
     * was started in {@link #begin}, the exception is recorded on the
     * span and the span is ended.
     *
     * @param ctx the observation context returned by {@link #begin}
     * @param t   the exception that occurred; must not be null
     */
    protected void error(ObservationContext ctx, Throwable t) {
        long durationMs = System.currentTimeMillis() - ctx.startTime();
        String errorClass = t.getClass().getSimpleName();

        recorder.recordClientError(ctx.method(), ctx.host(), errorClass, durationMs);

        if (ctx.span() != null) {
            ctx.span().recordException(t);
            ctx.span().end();
        }
    }

    /**
     * Resolves the URI template for the given request.
     *
     * <p>The default implementation returns {@code "UNKNOWN"}, matching the
     * pattern used by {@code ServerObservationHandler}. Subclasses can
     * override this to extract a route pattern (e.g., from a request
     * attribute set by a route-matching framework).
     *
     * @param request the outbound HTTP request
     * @return the URI template, or {@code "UNKNOWN"} if not available
     */
    protected String resolveUriTemplate(HttpRequest request) {
        return UNKNOWN_TEMPLATE;
    }

    /**
     * Resolves the host portion for metric tags, handling the default port case.
     *
     * <p>When a URI has no explicit port, {@code URI.getPort()} returns -1.
     * This method returns just the hostname in that case, avoiding
     * {@code "host:-1"} cardinality pollution.
     *
     * @param request the outbound HTTP request
     * @return the host string (with port if non-default, without otherwise)
     */
    private static String resolveHost(HttpRequest request) {
        int port = request.getURI().getPort();
        if (port == -1) {
            return request.getURI().getHost();
        }
        return request.getURI().getHost() + ":" + port;
    }

    /**
     * Extracts the HTTP status code from a {@link ClientHttpResponse},
     * returning {@code -1} if the status code cannot be read (e.g., when
     * the response body is not yet available or an I/O error occurs).
     *
     * @param response the HTTP response
     * @return the status code, or {@code -1} on failure
     */
    private static int extractStatusCode(ClientHttpResponse response) {
        try {
            return response.getStatusCode().value();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Holds the mutable state for a single client-side HTTP observation.
     *
     * <p>Instances are created by {@link #begin(HttpRequest)} and passed to
     * {@link #end(ObservationContext, ClientHttpResponse)} or
     * {@link #error(ObservationContext, Throwable)} to complete the observation.
     *
     * @since 0.2.0-alpha
     */
    public static final class ObservationContext {

        private final String method;
        private final String host;
        private final long startTime;
        private final Span span;
        private final String traceId;

        ObservationContext(String method, String host, long startTime, Span span, String traceId) {
            this.method = method;
            this.host = host;
            this.startTime = startTime;
            this.span = span;
            this.traceId = traceId;
        }

        /**
         * Returns the HTTP method of the observed request.
         *
         * @return the HTTP method (e.g., "GET", "POST")
         */
        public String method() {
            return method;
        }

        /**
         * Returns the resolved host tag for the observed request.
         *
         * @return the host string (e.g., "inventory-service:8081")
         */
        public String host() {
            return host;
        }

        /**
         * Returns the start time of the observation in milliseconds
         * since the epoch, as captured by {@link System#currentTimeMillis()}.
         *
         * @return the start time in epoch milliseconds
         */
        public long startTime() {
            return startTime;
        }

        /**
         * Returns the OpenTelemetry span for this observation, or
         * {@code null} if no tracer was configured.
         *
         * @return the OTel span, or null
         */
        public Span span() {
            return span;
        }

        /**
         * Returns the trace ID for this observation, or {@code null}
         * if no tracer was configured.
         *
         * @return the trace ID as a 32-character hex string, or null
         */
        public String traceId() {
            return traceId;
        }
    }
}
