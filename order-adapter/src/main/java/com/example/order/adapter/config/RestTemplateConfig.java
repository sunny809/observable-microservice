package com.example.order.adapter.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate configuration for synchronous outbound HTTP calls.
 *
 * <p>Provides a pre-configured {@link RestTemplate} bean for WMS service calls.
 * The {@link RestTemplateBuilder} is auto-provided by Spring Boot and the
 * {@code RestTemplateCustomizer} from o11y-kit ({@code HttpClientObservationAutoConfiguration})
 * automatically adds the {@code RestTemplateObservationInterceptor} to every
 * Spring-managed {@code RestTemplate} bean, enabling client-side HTTP metrics
 * and optional OpenTelemetry tracing without manual wiring.
 *
 * <p>This demonstrates the sync client observation path in o11y-kit, complementing
 * the existing WebClient-based adapters that use the WebFlux observation handler.
 *
 * @see com.example.order.adapter.outbound.wms.WmsRestTemplateAdapter
 * @see io.o11y.kit.autoconfigure.HttpClientObservationAutoConfiguration
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate for WMS service calls.
     *
     * <p>The base URL is sourced from {@link WmsAdapterProperties}. The
     * o11y-kit {@code RestTemplateCustomizer} will automatically append the
     * observation interceptor when the bean is created via {@link RestTemplateBuilder}.
     *
     * @param builder    the Spring Boot RestTemplateBuilder (auto-configured)
     * @param properties WMS service URL configuration
     * @return configured RestTemplate instance with observation interceptor
     */
    @Bean
    public RestTemplate wmsRestTemplate(RestTemplateBuilder builder,
                                        WmsAdapterProperties properties) {
        return builder
                .rootUri(properties.getBaseUrl())
                .build();
    }
}
