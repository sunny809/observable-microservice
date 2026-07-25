package io.o11y.kit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Generic business metrics SPI for recording application-level metrics
 * (counters, timers, gauges) without coupling to a specific metrics backend.
 *
 * <p>Default implementation uses Micrometer ({@link MicrometerMetricsAdapter}).
 * Users can provide their own implementation to route to OpenTelemetry,
 * Dropwizard Metrics, or any other backend.
 *
 * <p>Usage example:
 * <pre>{@code
 * public class OrderMetrics {
 *     private final BusinessMetricsPort metrics;
 *
 *     public void recordOrderPlaced() {
 *         metrics.increment("orders.placed", "status", "CREATED");
 *     }
 * }
 * }</pre>
 *
 * @since 0.5.0
 */
public interface BusinessMetricsPort {

    /**
     * Obtain or create a counter for the given name and tags.
     * @param name metric name (e.g., "orders.placed")
     * @param tags key-value pairs (e.g., "status", "CREATED")
     * @return the counter instance
     */
    Counter counter(String name, String... tags);

    /**
     * Obtain or create a timer for the given name and tags.
     * @param name metric name (e.g., "saga.duration")
     * @param tags key-value pairs
     * @return the timer instance
     */
    Timer timer(String name, String... tags);

    /**
     * Register a gauge that returns the given value.
     * @param name metric name
     * @param value the current value (may be updated later)
     * @param tags key-value pairs
     * @return the gauge instance
     */
    Gauge gauge(String name, Number value, String... tags);

    /** Increment a counter by 1. Convenience shortcut for {@code counter(name, tags).increment()}. */
    default void increment(String name, String... tags) {
        counter(name, tags).increment();
    }

    /** Record a duration to a timer. Convenience shortcut for {@code timer(name, tags).record(millis, MILLISECONDS)}. */
    default void recordDuration(String name, long millis, String... tags) {
        timer(name, tags).record(millis, TimeUnit.MILLISECONDS);
    }
}