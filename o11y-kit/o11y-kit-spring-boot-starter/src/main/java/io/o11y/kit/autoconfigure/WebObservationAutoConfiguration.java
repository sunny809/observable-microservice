package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.webflux.ClientObservationHandler;
import io.o11y.kit.webmvc.ServerObservationHandler;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.servlet.DispatcherServlet;

@AutoConfiguration(after = HttpMetricsAutoConfiguration.class)
@ConditionalOnWebApplication
public class WebObservationAutoConfiguration {

    @Configuration
    @ConditionalOnClass(DispatcherServlet.class)
    public static class WebMvcConfig {
        @Bean
        @ConditionalOnMissingBean
        public ServerObservationHandler serverObservationHandler(
                HttpMetricRecorder recorder) {
            return new ServerObservationHandler(recorder);
        }
    }

    @Configuration
    @ConditionalOnClass(DispatcherHandler.class)
    public static class WebFluxConfig {
        @Bean
        @ConditionalOnMissingBean
        public ClientObservationHandler clientObservationHandler(
                HttpMetricRecorder recorder,
                ObjectProvider<Tracer> tracerProvider) {
            Tracer tracer = tracerProvider.getIfAvailable();
            return new ClientObservationHandler(recorder, tracer);
        }
    }
}