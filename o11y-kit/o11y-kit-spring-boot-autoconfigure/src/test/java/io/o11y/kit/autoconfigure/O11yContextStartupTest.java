package io.o11y.kit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webflux.ClientObservationHandler;
import io.o11y.kit.spring.webmvc.ServerObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-cutting startup tests verifying that the o11y-kit auto-configurations
 * boot cleanly under both servlet and reactive web environments and that
 * downstream beans (recorder, server handler, client handler) are wired
 * correctly under realistic conditions.
 *
 * <p>These tests complement the per-class auto-configuration tests
 * ({@link HttpMetricsAutoConfigurationTest}, {@link ObservationWebFluxAutoConfigurationTest})
 * by exercising the auto-configurations together, the way an end-user
 * application would.
 */
class O11yContextStartupTest {

    private final WebApplicationContextRunner servletRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMetricsAutoConfiguration.class,
                    ObservationWebMvcAutoConfiguration.class,
                    ObservationWebFluxAutoConfiguration.class,
                    HttpClientObservationAutoConfiguration.class));

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMetricsAutoConfiguration.class,
                    ObservationWebMvcAutoConfiguration.class,
                    ObservationWebFluxAutoConfiguration.class,
                    HttpClientObservationAutoConfiguration.class));

    private final ReactiveWebApplicationContextRunner reactiveRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMetricsAutoConfiguration.class,
                    ObservationWebMvcAutoConfiguration.class,
                    ObservationWebFluxAutoConfiguration.class,
                    HttpClientObservationAutoConfiguration.class));

    @Test
    void servletContextLoadsHttpMetricsAndWebMvcBeans() {
        servletRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HttpMetricRecorder.class);
                    assertThat(ctx).hasSingleBean(ServerObservationHandler.class);
                    // ObservationWebMvcAutoConfiguration is active in servlet env;
                    // ObservationWebFluxAutoConfiguration is not.
                    assertThat(ctx).doesNotHaveBean(ClientObservationHandler.class);
                });
    }

    @Test
    void reactiveContextLoadsClientObservationHandler() {
        reactiveRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HttpMetricRecorder.class);
                    assertThat(ctx).hasSingleBean(ClientObservationHandler.class);
                    // Reactive runner is not a servlet env: WebMvc auto-config skips.
                    assertThat(ctx).doesNotHaveBean(ServerObservationHandler.class);
                });
    }

    @Test
    void userSuppliedHttpMetricRecorderWinsOverAutoConfig() {
        HttpMetricRecorder userRecorder = new HttpMetricRecorder() {
            @Override public void recordServerRequest(String m, String u, int s, long d) {}
            @Override public void recordClientRequest(String m, String h, int s, long d) {}
            @Override public void recordClientError(String m, String h, String e, long d) {}
        };
        servletRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean("userRecorder", HttpMetricRecorder.class, () -> userRecorder)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HttpMetricRecorder.class);
                    assertThat(ctx.getBean(HttpMetricRecorder.class)).isSameAs(userRecorder);
                });
    }

    @Test
    void missingMeterRegistryYieldsNoHttpMetricRecorderBean() {
        // Sprint 1.5 fix: HttpMetricsAutoConfiguration must back off without a MeterRegistry.
        // Use a non-web runner with only HttpMetricsAutoConfiguration so we exercise the
        // back-off behavior in isolation (the WebMvc/WebFlux auto-configs require a
        // recorder bean and would fail with cascading errors here).
        nonWebRunner
                .withConfiguration(AutoConfigurations.of(HttpMetricsAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(HttpMetricRecorder.class);
                });
    }

    @Test
    void clientEnabledFalsePropertyStillBootsContext() {
        // The gate isn't wired into bean creation yet, but the property must at least
        // bind cleanly without breaking startup once O11yKitProperties is registered.
        servletRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("o11y.kit.client.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(O11yKitProperties.class);
                    O11yKitProperties props = ctx.getBean(O11yKitProperties.class);
                    assertThat(props.getClient().isEnabled()).isFalse();
                    assertThat(props.getClient().getMetrics().isEnabled()).isTrue();
                });
    }

    @Test
    void contextLoadsWithoutWebClientOnClasspath() {
        // ObservationWebFluxAutoConfiguration is gated on WebClient.class — when it's
        // missing, the auto-config silently skips and the rest of the context still loads.
        reactiveRunner
                .withClassLoader(new FilteredClassLoader(WebClient.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(HttpMetricRecorder.class);
                    assertThat(ctx).doesNotHaveBean(ClientObservationHandler.class);
                });
    }
}
