package io.o11y.kit.metrics;

import io.micrometer.core.instrument.*;
import java.util.Objects;

/**
 * Micrometer-backed implementation of {@link BusinessMetricsPort}.
 * All metrics are registered with the given {@link MeterRegistry}.
 *
 * <p>Thread-safe: delegates to the Micrometer registry which is thread-safe.
 *
 * @since 0.5.0
 */
public class MicrometerMetricsAdapter implements BusinessMetricsPort {

    private final MeterRegistry registry;

    public MicrometerMetricsAdapter(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
    }

    @Override
    public Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    @Override
    public Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }

    @Override
    public Timer timer(String name, String description, String... tags) {
        return Timer.builder(name).description(description).tags(tags).register(registry);
    }

    @Override
    public Gauge gauge(String name, Number value, String... tags) {
        return Gauge.builder(name, () -> value).tags(tags).strongReference(true).register(registry);
    }
}