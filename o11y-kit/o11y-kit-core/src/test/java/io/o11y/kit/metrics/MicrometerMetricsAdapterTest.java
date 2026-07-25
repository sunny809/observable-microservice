package io.o11y.kit.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricsAdapterTest {

    private MicrometerMetricsAdapter adapter;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new MicrometerMetricsAdapter(registry);
    }

    @Test
    void shouldCreateCounter() {
        adapter.increment("test.counter", "key", "value");
        double count = registry.counter("test.counter", "key", "value").count();
        assertEquals(1.0, count, 0.001);
    }

    @Test
    void shouldCreateTimer() {
        adapter.recordDuration("test.timer", 100, "key", "value");
        long count = registry.timer("test.timer", "key", "value").count();
        assertEquals(1L, count);
    }

    @Test
    void shouldCreateGauge() {
        adapter.gauge("test.gauge", 42.0, "key", "value");
        double value = registry.get("test.gauge").tags("key", "value").gauge().value();
        assertEquals(42.0, value, 0.001);
    }
}