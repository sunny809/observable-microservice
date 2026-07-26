package io.o11y.kit.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.o11y.kit.http.HttpMetricRecorder;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed implementation of {@link HttpMetricRecorder}.
 *
 * <p>Exports the following metrics for server-side (inbound) requests:
 * <ul>
 *   <li>{@code o11y.server.requests} — Timer, tags: method, uri, status</li>
 * </ul>
 *
 * <p>And for client-side (outbound) requests:
 * <ul>
 *   <li>{@code o11y.client.requests} — Timer, tags: method, host, status</li>
 *   <li>{@code o11y.client.errors} — Counter, tags: method, host, error</li>
 * </ul>
 *
 * <p>Tags avoid high-cardinality explosion by using URI patterns (not actual paths)
 * and host names (not full URLs).
 *
 * @since 0.1.0
 */
public class MicrometerHttpMetricRecorder extends HttpMetricRecorder {

    private static final String SERVER_REQUESTS = "o11y.server.requests";
    private static final String CLIENT_REQUESTS = "o11y.client.requests";
    private static final String CLIENT_ERRORS = "o11y.client.errors";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Timer> serverTimerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> clientTimerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> clientErrorCache = new ConcurrentHashMap<>();

    public MicrometerHttpMetricRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordServerRequest(String method, String uri, int statusCode, long durationMs) {
        String statusGroup = statusCode / 100 + "xx";
        String key = method + "|" + uri + "|" + statusGroup;
        Timer timer = serverTimerCache.computeIfAbsent(key, k ->
                Timer.builder(SERVER_REQUESTS)
                        .tag("method", method)
                        .tag("uri", uri)
                        .tag("status", statusGroup)
                        .description("HTTP server request round-trip time")
                        .register(registry));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordClientRequest(String method, String host, int statusCode, long durationMs) {
        String statusGroup = statusCode / 100 + "xx";
        String key = method + "|" + host + "|" + statusGroup;
        Timer timer = clientTimerCache.computeIfAbsent(key, k ->
                Timer.builder(CLIENT_REQUESTS)
                        .tag("method", method)
                        .tag("host", host)
                        .tag("status", statusGroup)
                        .description("HTTP client request round-trip time")
                        .register(registry));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordClientError(String method, String host, String errorClass, long durationMs) {
        String key = method + "|" + host + "|" + errorClass;
        Counter counter = clientErrorCache.computeIfAbsent(key, k ->
                Counter.builder(CLIENT_ERRORS)
                        .tag("method", method)
                        .tag("host", host)
                        .tag("error", errorClass)
                        .description("HTTP client request errors")
                        .register(registry));
        counter.increment();
    }
}
