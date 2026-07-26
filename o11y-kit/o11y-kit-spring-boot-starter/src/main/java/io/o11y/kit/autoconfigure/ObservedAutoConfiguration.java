package io.o11y.kit.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.o11y.kit.spring.aop.Observed;
import io.o11y.kit.spring.aop.ObservedAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration that enables {@link ObservedAspect} for the
 * {@link Observed} annotation.
 *
 * <p>Activates when AspectJ and Micrometer are on the classpath.
 * Enables AspectJ auto-proxying via {@link EnableAspectJAutoProxy}.
 *
 * @since 0.4.0-beta
 */
@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, Observed.class})
@EnableAspectJAutoProxy
public class ObservedAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObservedAspect observedAspect(MeterRegistry meterRegistry) {
        return new ObservedAspect(meterRegistry);
    }
}