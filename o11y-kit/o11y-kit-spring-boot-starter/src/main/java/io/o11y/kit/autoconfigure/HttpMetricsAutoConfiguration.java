package io.o11y.kit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.micrometer.MicrometerHttpMetricRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for o11y-kit HTTP metrics.
 *
 * <p>Creates a {@link HttpMetricRecorder} bean backed by Micrometer
 * when a {@link MeterRegistry} is available.
 *
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@EnableConfigurationProperties(O11yKitProperties.class)
public class HttpMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HttpMetricRecorder httpMetricRecorder(MeterRegistry meterRegistry) {
        return new MicrometerHttpMetricRecorder(meterRegistry);
    }
}
