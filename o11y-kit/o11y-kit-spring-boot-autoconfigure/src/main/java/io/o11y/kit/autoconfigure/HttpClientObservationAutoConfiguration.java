package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.http.O11yKitOrders;
import io.o11y.kit.webmvc.client.RestClientObservationInterceptor;
import io.o11y.kit.webmvc.client.RestTemplateObservationInterceptor;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration that wires o11y-kit observation interceptors into
 * Spring-managed {@link RestTemplate} and {@link RestClient} beans.
 *
 * <p>Uses Spring Boot's {@link RestTemplateCustomizer} and
 * {@link RestClientCustomizer} SPIs so that any user-declared
 * {@code RestTemplate} or {@code RestClient.Builder} bean automatically
 * receives the observation interceptor without manual wiring.
 *
 * <p><strong>Ordering:</strong> The customizers are annotated with
 * {@code @Order(O11yKitOrders.CLIENT_OBSERVATION)} so they run after
 * user-provided customizers, ensuring the observation interceptor is
 * always appended to the interceptor list.
 *
 * <p><strong>Configuration:</strong> Both customizers are gated by
 * {@code o11y.kit.client.enabled=true} (the default). Set this property
 * to {@code false} to disable client-side observation wiring.
 *
 * <p><strong>Deduplication:</strong> If a user manually adds a
 * {@link RestTemplateObservationInterceptor} to their {@code RestTemplate},
 * the customizer detects this and skips adding a second instance.
 *
 * <p><strong>OpenTelemetry:</strong> The {@link Tracer} bean is optional.
 * When present, interceptors create OTel spans for each outbound call.
 * When absent, only Micrometer metrics are recorded.
 *
 * @since 0.2.0-alpha
 */
@AutoConfiguration(after = HttpMetricsAutoConfiguration.class)
@ConditionalOnBean(HttpMetricRecorder.class)
@ConditionalOnProperty(
        prefix = "o11y.kit.client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpClientObservationAutoConfiguration {

    /**
     * Registers a {@link RestTemplateCustomizer} that adds the
     * {@link RestTemplateObservationInterceptor} to every
     * Spring-managed {@link RestTemplate} bean.
     *
     * <p>The interceptor is only added if the template does not already
     * contain one, preventing double-registration when a user manually
     * configures their template.
     *
     * @param recorder    the metric recorder
     * @param tracerProvider the OpenTelemetry tracer provider (optional; may be empty)
     * @return the customizer bean
     */
    @Bean
    @Order(O11yKitOrders.CLIENT_OBSERVATION)
    public RestTemplateCustomizer observationRestTemplateCustomizer(
            HttpMetricRecorder recorder,
            ObjectProvider<Tracer> tracerProvider) {
        return restTemplate -> {
            boolean alreadyPresent = restTemplate.getInterceptors().stream()
                    .anyMatch(i -> i instanceof RestTemplateObservationInterceptor);
            if (!alreadyPresent) {
                Tracer tracer = tracerProvider.getIfAvailable();
                restTemplate.getInterceptors()
                        .add(new RestTemplateObservationInterceptor(recorder, tracer));
            }
        };
    }

    /**
     * Registers a {@link RestClientCustomizer} that adds the o11y-kit
     * observation interceptor to every Spring-managed
     * {@link RestClient.Builder} bean.
     *
     * <p>Since Spring 6.2, {@code RestClient.Builder.requestInterceptor()}
     * accepts the same {@code ClientHttpRequestInterceptor} interface as
     * {@code RestTemplate}, so the same interceptor implementation is reused.
     *
     * @param recorder    the metric recorder
     * @param tracerProvider the OpenTelemetry tracer provider (optional; may be empty)
     * @return the customizer bean
     */
    @Bean
    @Order(O11yKitOrders.CLIENT_OBSERVATION)
    public RestClientCustomizer observationRestClientCustomizer(
            HttpMetricRecorder recorder,
            ObjectProvider<Tracer> tracerProvider) {
        return builder -> {
            Tracer tracer = tracerProvider.getIfAvailable();
            builder.requestInterceptor(
                    RestClientObservationInterceptor.create(recorder, tracer));
        };
    }
}
