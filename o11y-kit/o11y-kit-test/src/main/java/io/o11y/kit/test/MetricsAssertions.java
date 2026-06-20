package io.o11y.kit.test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.assertj.core.api.Assertions;

/**
 * Fluent assertion API for verifying o11y-kit HTTP metrics in a Micrometer
 * {@link MeterRegistry}.
 *
 * <p>Usage:
 * <pre>{@code
 * MetricsAssertions.assertThat(registry)
 *     .hasClientTimer("GET", "inventory-service:8081", 200)
 *     .hasServerTimer("POST", "/api/v1/orders", 201);
 * }</pre>
 *
 * <p>The {@code status} parameter is the raw HTTP status code (e.g., 200, 404, 500).
 * The assertion internally converts it to a status group tag (e.g., "2xx", "4xx", "5xx")
 * to match the convention used by o11y-kit's {@code MicrometerHttpMetricRecorder}.
 *
 * @since 0.2.0-alpha
 */
public class MetricsAssertions {

    private static final String CLIENT_TIMER = "o11y.client.requests";
    private static final String CLIENT_ERROR_COUNTER = "o11y.client.errors";
    private static final String SERVER_TIMER = "o11y.server.requests";

    private final MeterRegistry registry;

    private MetricsAssertions(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Creates a new {@code MetricsAssertions} rooted at the given registry.
     *
     * @param registry the Micrometer registry to assert against; must not be null
     * @return a new assertions instance
     */
    public static MetricsAssertions assertThat(MeterRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry must not be null");
        }
        return new MetricsAssertions(registry);
    }

    /**
     * Asserts that the registry contains a client-side timer for the given
     * method, host, and status combination.
     *
     * <p>Matches against the {@code o11y.client.requests} timer with tags
     * {@code method}, {@code host}, and {@code status} (status group,
     * e.g., "2xx").
     *
     * @param method the HTTP method (e.g., "GET")
     * @param host   the target host tag (e.g., "inventory-service:8081")
     * @param status the HTTP status code (will be matched as a status group)
     * @return this assertions instance for chaining
     */
    public MetricsAssertions hasClientTimer(String method, String host, int status) {
        String statusGroup = toStatusGroup(status);

        Timer timer = registry.find(CLIENT_TIMER)
                .tag("method", method)
                .tag("host", host)
                .tag("status", statusGroup)
                .timer();

        Assertions.assertThat(timer)
                .as("Expected o11y.client.requests timer with method=%s, host=%s, status=%s",
                        method, host, statusGroup)
                .isNotNull();

        return this;
    }

    /**
     * Asserts that the registry contains a client-side error counter for the
     * given method, host, and error class combination.
     *
     * <p>Matches against the {@code o11y.client.errors} counter with tags
     * {@code method}, {@code host}, and {@code error}.
     *
     * @param method    the HTTP method
     * @param host      the target host tag
     * @param error     the error class simple name (e.g., "ConnectException")
     * @return this assertions instance for chaining
     */
    public MetricsAssertions hasClientError(String method, String host, String error) {
        var counter = registry.find(CLIENT_ERROR_COUNTER)
                .tag("method", method)
                .tag("host", host)
                .tag("error", error)
                .counter();

        Assertions.assertThat(counter)
                .as("Expected o11y.client.errors counter with method=%s, host=%s, error=%s",
                        method, host, error)
                .isNotNull();

        return this;
    }

    /**
     * Asserts that the registry contains a server-side timer for the given
     * method, URI, and status combination.
     *
     * <p>Matches against the {@code o11y.server.requests} timer with tags
     * {@code method}, {@code uri}, and {@code status} (status group,
     * e.g., "2xx").
     *
     * @param method the HTTP method
     * @param uri    the URI pattern tag (e.g., "/api/v1/orders")
     * @param status the HTTP status code (will be matched as a status group)
     * @return this assertions instance for chaining
     */
    public MetricsAssertions hasServerTimer(String method, String uri, int status) {
        String statusGroup = toStatusGroup(status);

        Timer timer = registry.find(SERVER_TIMER)
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", statusGroup)
                .timer();

        Assertions.assertThat(timer)
                .as("Expected o11y.server.requests timer with method=%s, uri=%s, status=%s",
                        method, uri, statusGroup)
                .isNotNull();

        return this;
    }

    private static String toStatusGroup(int statusCode) {
        return (statusCode / 100) + "xx";
    }
}
