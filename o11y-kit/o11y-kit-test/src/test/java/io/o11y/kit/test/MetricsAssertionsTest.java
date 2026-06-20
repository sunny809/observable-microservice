package io.o11y.kit.test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MetricsAssertions}.
 */
class MetricsAssertionsTest {

    @Test
    void hasClientTimerFindsMatchingTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        // Record a client timer the same way MicrometerHttpMetricRecorder does
        registry.timer("o11y.client.requests",
                        "method", "GET",
                        "host", "inventory-service:8081",
                        "status", "2xx")
                .record(() -> {});

        assertThatNoException().isThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientTimer("GET", "inventory-service:8081", 200)
        );
    }

    @Test
    void hasClientTimerFailsWhenTimerMissing() {
        MeterRegistry registry = new SimpleMeterRegistry();

        // No timers registered — assertion should fail
        assertThatThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientTimer("GET", "missing-host:8081", 200)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void hasClientTimerFailsWhenStatusGroupDiffers() {
        MeterRegistry registry = new SimpleMeterRegistry();

        // Record a 5xx timer
        registry.timer("o11y.client.requests",
                        "method", "GET",
                        "host", "inventory-service:8081",
                        "status", "5xx")
                .record(() -> {});

        // Looking for 2xx should fail
        assertThatThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientTimer("GET", "inventory-service:8081", 200)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void hasServerTimerFindsMatchingTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        registry.timer("o11y.server.requests",
                        "method", "POST",
                        "uri", "/api/v1/orders",
                        "status", "2xx")
                .record(() -> {});

        assertThatNoException().isThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasServerTimer("POST", "/api/v1/orders", 201)
        );
    }

    @Test
    void hasServerTimerFailsWhenTimerMissing() {
        MeterRegistry registry = new SimpleMeterRegistry();

        assertThatThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasServerTimer("GET", "/not-found", 404)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void hasClientErrorFindsMatchingCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();

        registry.counter("o11y.client.errors",
                        "method", "GET",
                        "host", "inventory-service:8081",
                        "error", "ConnectException")
                .increment();

        assertThatNoException().isThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientError("GET", "inventory-service:8081", "ConnectException")
        );
    }

    @Test
    void hasClientErrorFailsWhenCounterMissing() {
        MeterRegistry registry = new SimpleMeterRegistry();

        assertThatThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientError("GET", "missing-host:8081", "TimeoutException")
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    void chainedAssertionsAllPass() {
        MeterRegistry registry = new SimpleMeterRegistry();

        registry.timer("o11y.client.requests",
                        "method", "GET",
                        "host", "inventory-service:8081",
                        "status", "2xx")
                .record(() -> {});

        registry.timer("o11y.server.requests",
                        "method", "POST",
                        "uri", "/api/v1/orders",
                        "status", "2xx")
                .record(() -> {});

        assertThatNoException().isThrownBy(() ->
                MetricsAssertions.assertThat(registry)
                        .hasClientTimer("GET", "inventory-service:8081", 200)
                        .hasServerTimer("POST", "/api/v1/orders", 201)
        );
    }

    @Test
    void assertThatRejectsNullRegistry() {
        assertThatThrownBy(() -> MetricsAssertions.assertThat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }
}
