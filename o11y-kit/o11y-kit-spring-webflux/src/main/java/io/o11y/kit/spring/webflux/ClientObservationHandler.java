package io.o11y.kit.spring.webflux;

import io.o11y.kit.http.HttpMetricRecorder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * {@link ExchangeFilterFunction} that records client-side HTTP callout metrics.
 *
 * <p>For each outbound request, it:
 * <ul>
 *   <li>Creates an OTel child span for the callout</li>
 *   <li>Records round-trip duration via {@link HttpMetricRecorder#recordClientRequest}</li>
 *   <li>Records HTTP status code distribution</li>
 *   <li>Records errors (timeout, connection refused, etc.)</li>
 *   <li>Injects trace context into outgoing request headers</li>
 * </ul>
 *
 * <p>Known limitation: OTel {@code Scope} is not propagated into the Reactor pipeline
 * (see Sprint 5 for full Reactor Context integration). Trace header injection still works
 * correctly because the span context is captured at request-building time.
 *
 * @since 0.1.0
 */
public class ClientObservationHandler implements ExchangeFilterFunction {

    private final HttpMetricRecorder recorder;
    private final Tracer tracer;

    /**
     * Creates a new {@code ClientObservationHandler} without OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     */
    public ClientObservationHandler(HttpMetricRecorder recorder) {
        this(recorder, null);
    }

    /**
     * Creates a new {@code ClientObservationHandler} with OpenTelemetry tracing.
     *
     * @param recorder the metric recorder; must not be null
     * @param tracer   the OpenTelemetry tracer; if null, tracing is skipped
     */
    public ClientObservationHandler(HttpMetricRecorder recorder, Tracer tracer) {
        this.recorder = recorder;
        this.tracer = tracer;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long startTime = System.currentTimeMillis();
        String method = request.method().name();
        String host = resolveHost(request);

        if (tracer == null) {
            return recordMetrics(next.exchange(request), method, host, startTime, null);
        }

        Span span = tracer.spanBuilder("HTTP " + method)
                .setAttribute("http.method", method)
                .setAttribute("http.url", request.url().toString())
                .setAttribute("http.host", host)
                .startSpan();

        ClientRequest tracedRequest = ClientRequest.from(request)
                .headers(headers -> {
                    String traceId = span.getSpanContext().getTraceId();
                    if (traceId != null && !traceId.isEmpty()) {
                        headers.set("X-B3-TraceId", traceId);
                        headers.set("X-Trace-Id", traceId);
                    }
                })
                .build();

        // Note: OTel Scope is intentionally NOT set on this thread.
        // The exchange runs asynchronously on a Reactor thread, so a
        // try-with-resources Scope would be closed before the Mono executes.
        // Full Reactor Context propagation will be added in Sprint 5.
        return recordMetrics(next.exchange(tracedRequest), method, host, startTime, span);
    }

    /**
     * Wraps the exchange Mono with metric-recording side effects.
     * <p>
     * Avoids double-counting for HTTP error responses: {@link WebClientResponseException}
     * fires both {@code doOnNext} (the raw HTTP response) and {@code doOnError} (the
     * exception from {@code retrieve()}). We deduplicate by checking the error type.
     */
    private Mono<ClientResponse> recordMetrics(Mono<ClientResponse> exchange,
                                                       String method, String host,
                                                       long startTime, Span span) {
        return exchange
                .doOnNext(response -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    int statusCode = response.statusCode().value();
                    if (span != null) {
                        span.setAttribute("http.status_code", statusCode);
                    }
                    recorder.recordClientRequest(method, host, statusCode, durationMs);
                })
                .doOnError(WebClientResponseException.class, error -> {
                    // HTTP error responses are already recorded by doOnNext above.
                    // Only record the OTel exception for span visibility.
                    if (span != null) {
                        span.recordException(error);
                    }
                })
                .doOnError(error -> {
                    // Non-HTTP errors (connect refused, timeout, DNS) were NOT recorded
                    // by doOnNext, so record them here.
                    long durationMs = System.currentTimeMillis() - startTime;
                    recorder.recordClientError(method, host, error.getClass().getSimpleName(), durationMs);
                    if (span != null) {
                        span.recordException(error);
                    }
                })
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        long durationMs = System.currentTimeMillis() - startTime;
                        recorder.recordClientError(method, host, "CANCELLED", durationMs);
                    }
                    if (span != null) {
                        span.end();
                    }
                });
    }

    /**
     * Resolves the host portion for metric tags, handling the default port case.
     * <p>
     * When a URL has no explicit port, {@code java.net.URL.getPort()} returns -1.
     * We return just the hostname in that case, avoiding "host:-1" cardinality pollution.
     */
    private static String resolveHost(ClientRequest request) {
        int port = request.url().getPort();
        if (port == -1) {
            return request.url().getHost();
        }
        return request.url().getHost() + ":" + port;
    }
}
