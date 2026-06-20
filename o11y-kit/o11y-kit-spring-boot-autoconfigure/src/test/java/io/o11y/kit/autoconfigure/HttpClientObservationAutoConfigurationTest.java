package io.o11y.kit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webmvc.client.RestTemplateObservationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpClientObservationAutoConfiguration}.
 *
 * <p>Verifies that the RestTemplate/RestClient customizer beans are created
 * under the right conditions, that the interceptor is actually added to
 * RestTemplate beans, and that the {@code o11y.kit.client.enabled=false}
 * property suppresses the customizers.
 *
 * @since 0.2.0-alpha
 */
class HttpClientObservationAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMetricsAutoConfiguration.class,
                    ObservationWebMvcAutoConfiguration.class,
                    HttpClientObservationAutoConfiguration.class));

    @Test
    void customizerBeansAreCreatedWhenMeterRegistryPresent() {
        runner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(RestTemplateCustomizer.class);
                    assertThat(ctx).hasSingleBean(RestClientCustomizer.class);
                });
    }

    @Test
    void customizerAddsInterceptorToRestTemplate() {
        runner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(RestTemplateCustomizer.class);

                    // Create a RestTemplate and apply the customizer manually
                    // (ApplicationContextRunner doesn't auto-create RestTemplate beans)
                    RestTemplateCustomizer customizer = ctx.getBean(RestTemplateCustomizer.class);
                    RestTemplate rt = new RestTemplate();
                    customizer.customize(rt);

                    long observationInterceptors = rt.getInterceptors().stream()
                            .filter(i -> i instanceof RestTemplateObservationInterceptor)
                            .count();
                    assertThat(observationInterceptors).isEqualTo(1);
                });
    }

    @Test
    void customizerDoesNotDuplicateInterceptor() {
        runner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    RestTemplateCustomizer customizer = ctx.getBean(RestTemplateCustomizer.class);
                    RestTemplate rt = new RestTemplate();
                    // Apply once
                    customizer.customize(rt);
                    // Apply again — should not duplicate
                    customizer.customize(rt);

                    long observationInterceptors = rt.getInterceptors().stream()
                            .filter(i -> i instanceof RestTemplateObservationInterceptor)
                            .count();
                    assertThat(observationInterceptors).isEqualTo(1);
                });
    }

    @Test
    void clientEnabledFalseSuppressesCustomizers() {
        runner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("o11y.kit.client.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(RestTemplateCustomizer.class);
                    assertThat(ctx).doesNotHaveBean(RestClientCustomizer.class);
                });
    }

    @Test
    void noCustomizerBeansWhenMeterRegistryMissing() {
        // Only load HttpClientObservationAutoConfiguration in isolation,
        // not ObservationWebMvcAutoConfiguration which requires HttpMetricRecorder
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HttpClientObservationAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(RestTemplateCustomizer.class);
                    assertThat(ctx).doesNotHaveBean(RestClientCustomizer.class);
                });
    }
}
