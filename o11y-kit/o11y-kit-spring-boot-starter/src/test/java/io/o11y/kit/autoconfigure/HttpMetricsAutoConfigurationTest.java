package io.o11y.kit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.o11y.kit.http.HttpMetricRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpMetricsAutoConfiguration}.
 */
class HttpMetricsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpMetricsAutoConfiguration.class));

    @Test
    void shouldProvideHttpMetricRecorderBean() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx.getBean(HttpMetricRecorder.class)).isNotNull();
                });
    }

    @Test
    void shouldNotCreateBeanWhenMeterRegistryMissing() {
        runner.run(ctx -> {
            assertThat(ctx.getBeansOfType(HttpMetricRecorder.class)).isEmpty();
        });
    }

    @Test
    void shouldRespectExistingHttpMetricRecorderBean() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(HttpMetricRecorder.class, () -> new HttpMetricRecorder() {
                    @Override
                    public void recordServerRequest(String method, String uri, int statusCode, long durationMs) {}
                    @Override
                    public void recordClientRequest(String method, String host, int statusCode, long durationMs) {}
                    @Override
                    public void recordClientError(String method, String host, String errorClass, long durationMs) {}
                })
                .run(ctx -> {
                    assertThat(ctx.getBeansOfType(HttpMetricRecorder.class)).hasSize(1);
                });
    }
}