package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.micrometer.MicrometerHttpMetricRecorder;
import io.o11y.kit.webflux.ClientObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservationWebFluxAutoConfiguration}.
 */
class ObservationWebFluxAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMetricsAutoConfiguration.class,
                    ObservationWebFluxAutoConfiguration.class))
            .withBean(SimpleMeterRegistry.class);

    @Test
    void shouldProvideClientObservationHandlerBean() {
        runner.run(ctx -> {
            assertThat(ctx.getBean(ClientObservationHandler.class)).isNotNull();
        });
    }

    @Test
    void shouldRespectExistingClientObservationHandlerBean() {
        var mockRecorder = new HttpMetricRecorder() {
            @Override public void recordServerRequest(String m, String u, int s, long d) {}
            @Override public void recordClientRequest(String m, String h, int s, long d) {}
            @Override public void recordClientError(String m, String h, String e, long d) {}
        };
        runner.withBean(ClientObservationHandler.class,
                        () -> new ClientObservationHandler(mockRecorder))
                .run(ctx -> {
                    assertThat(ctx.getBeansOfType(ClientObservationHandler.class)).hasSize(1);
                });
    }
}
