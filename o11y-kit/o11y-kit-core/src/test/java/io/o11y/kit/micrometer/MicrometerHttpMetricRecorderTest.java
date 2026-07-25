package io.o11y.kit.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MicrometerHttpMetricRecorder}.
 */
class MicrometerHttpMetricRecorderTest {

    private MeterRegistry registry;
    private MicrometerHttpMetricRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new MicrometerHttpMetricRecorder(registry);
    }

    @Test
    void shouldRecordServerRequest() {
        recorder.recordServerRequest("POST", "/api/v1/orders", 200, 150L);

        var timer = registry.find("o11y.server.requests")
                .tag("method", "POST")
                .tag("uri", "/api/v1/orders")
                .tag("status", "2xx")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(150);
    }

    @Test
    void shouldRecordClientRequest() {
        recorder.recordClientRequest("GET", "inventory:8081", 200, 80L);

        var timer = registry.find("o11y.client.requests")
                .tag("method", "GET")
                .tag("host", "inventory:8081")
                .tag("status", "2xx")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordClientError() {
        recorder.recordClientError("POST", "wms:8082", "ConnectException", 5000L);

        var counter = registry.find("o11y.client.errors")
                .tag("method", "POST")
                .tag("host", "wms:8082")
                .tag("error", "ConnectException")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void shouldIncrementCounterOnRepeatedErrors() {
        recorder.recordClientError("POST", "wms:8082", "TimeoutException", 1000L);
        recorder.recordClientError("POST", "wms:8082", "TimeoutException", 2000L);

        var counter = registry.find("o11y.client.errors")
                .tag("error", "TimeoutException")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2);
    }

    @Test
    void shouldUseStatusGroupForServerRequests() {
        recorder.recordServerRequest("GET", "/api/orders", 404, 30L);
        recorder.recordServerRequest("GET", "/api/orders", 500, 50L);

        var timer4xx = registry.find("o11y.server.requests")
                .tag("status", "4xx")
                .timer();
        var timer5xx = registry.find("o11y.server.requests")
                .tag("status", "5xx")
                .timer();
        assertThat(timer4xx).isNotNull();
        assertThat(timer5xx).isNotNull();
        assertThat(timer4xx.count()).isEqualTo(1);
        assertThat(timer5xx.count()).isEqualTo(1);
    }
}
