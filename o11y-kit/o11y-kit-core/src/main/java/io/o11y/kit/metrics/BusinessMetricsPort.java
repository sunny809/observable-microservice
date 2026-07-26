package io.o11y.kit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.common.lang.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * Business metrics SPI for recording application-level metrics (counters,
 * timers, gauges). The default implementation ({@link MicrometerMetricsAdapter})
 * uses Micrometer.
 *
 * <p>The SPI returns Micrometer types ({@link Counter}, {@link Timer},
 * {@link Gauge}) for fluent API compatibility. Custom implementations can
 * return Micrometer-compatible stubs from a test harness, or throw
 * {@link UnsupportedOperationException} from methods they don't use.
 *
 * <p>Unless you need a different metrics backend, the simplest path is to
 * include {@code o11y-kit-core} and use the auto-configured
 * {@link MicrometerMetricsAdapter}.
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
     * Obtain or create a timer with a human-readable description.
     * @param name metric name
     * @param description description shown in Prometheus HELP output
     * @param tags key-value pairs
     * @return the timer instance
     */
    Timer timer(String name, String description, String... tags);

    /**
     * Register a gauge that reports the given value. The value is a snapshot
     * taken at registration time; for dynamic values, use {@link #counter(String, String...)}
     * with appropriate tags, or obtain a meter reference from the returned {@link Gauge}
     * and update it directly.
     * @param name metric name
     * @param value the value to report (snapshot at registration time)
     * @param tags key-value pairs
     * @return the gauge instance
     */
    Gauge gauge(String name, @Nullable Number value, String... tags);

    /** Increment a counter by 1. Convenience shortcut for {@code counter(name, tags).increment()}. */
    default void increment(String name, String... tags) {
        requireEvenTags(tags);
        counter(name, tags).increment();
    }

    /** Record a duration to a timer. Convenience shortcut for {@code timer(name, tags).record(millis, MILLISECONDS)}. */
    default void recordDuration(String name, long millis, String... tags) {
        requireEvenTags(tags);
        timer(name, tags).record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Validates that the tags array has an even number of elements (key-value pairs).
     */
    private void requireEvenTags(String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Tags must be key-value pairs (even number of elements), got " + tags.length);
        }
    }
}