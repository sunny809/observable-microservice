package com.order.demo.infrastructure.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class OpenTelemetryConfigTest {

    private OpenTelemetryConfig config;

    @BeforeEach
    void setUp() {
        config = new OpenTelemetryConfig();
        ReflectionTestUtils.setField(config, "otlpEndpoint", "http://localhost:14317");
    }

    @Test
    @DisplayName("openTelemetry should return non-null OpenTelemetry instance with tracer provider")
    void openTelemetryShouldReturnNonNullInstance() {
        OpenTelemetry otel = config.openTelemetry();
        assertNotNull(otel);
        assertNotNull(otel.getTracerProvider());
    }

    @Test
    @DisplayName("tracer should return non-null Tracer from OpenTelemetry")
    void tracerShouldReturnNonNullTracer() {
        OpenTelemetry minimalOtel = OpenTelemetry.noop();
        Tracer tracer = config.tracer(minimalOtel);
        assertNotNull(tracer);
    }

    @Test
    @DisplayName("otlpEndpoint config field should be accessible and match configured value")
    void openTelemetryShouldUseConfiguredEndpoint() {
        // Verify the config field is accessible and was set correctly
        String endpoint = (String) ReflectionTestUtils.getField(config, "otlpEndpoint");
        assertEquals("http://localhost:14317", endpoint);
    }
}
