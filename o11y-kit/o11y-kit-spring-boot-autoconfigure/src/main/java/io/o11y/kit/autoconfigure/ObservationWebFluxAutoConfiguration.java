package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webflux.ClientObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration that provides the {@link ClientObservationHandler} bean
 * for WebClient-based outbound HTTP callout metrics.
 *
 * <p>Activates only in a reactive web application context.
 * Can be disabled by setting {@code o11y.kit.client.enabled=false}.
 * Users can apply this handler to individual WebClient instances via:
 * <pre>{@code
 *   webClientBuilder.filter(clientObservationHandler);
 * }</pre>
 *
 * @since 0.1.1
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "o11y.kit.client", name = "enabled", matchIfMissing = true)
public class ObservationWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientObservationHandler clientObservationHandler(HttpMetricRecorder recorder) {
        return new ClientObservationHandler(recorder);
    }
}
