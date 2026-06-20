package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webmvc.ServerObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration that registers the {@link ServerObservationHandler}
 * interceptor for Spring MVC controllers.
 *
 * <p>By default, management endpoints under {@code /actuator/**} are excluded
 * from HTTP metrics to avoid self-observation and Prometheus scrape feedback loops.
 *
 * <p>Activates only in a servlet web application context.
 *
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservationWebMvcAutoConfiguration {

    /** Default exclusion pattern for actuator and health endpoints. */
    static final String[] DEFAULT_EXCLUDE_PATTERNS = {"/actuator/**", "/health/**"};

    @Bean
    @ConditionalOnMissingBean
    public ServerObservationHandler serverObservationHandler(HttpMetricRecorder recorder) {
        return new ServerObservationHandler(recorder);
    }

    @Bean
    public WebMvcConfigurer observationWebMvcConfigurer(ServerObservationHandler serverHandler) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(serverHandler)
                        .addPathPatterns("/**")
                        .excludePathPatterns(DEFAULT_EXCLUDE_PATTERNS);
            }
        };
    }
}
