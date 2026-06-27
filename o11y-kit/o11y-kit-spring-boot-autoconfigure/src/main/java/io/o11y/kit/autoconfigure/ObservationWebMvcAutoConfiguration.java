package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webmvc.ServerObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration that registers the {@link ServerObservationHandler}
 * interceptor for Spring MVC controllers.
 *
 * <p>Behaviour is controlled via the {@code o11y.kit.server.*} properties:
 * <ul>
 *   <li>{@code o11y.kit.server.enabled} — master switch (default: {@code true})</li>
 *   <li>{@code o11y.kit.server.exclude-patterns} — URL patterns to skip (default:
 *       {@code /actuator/**}, {@code /health/**})</li>
 *   <li>{@code o11y.kit.server.metrics.enabled} — metrics emission switch
 *       (default: {@code true})</li>
 * </ul>
 *
 * <p>Activates only in a servlet web application context. Can be disabled
 * entirely by setting {@code o11y.kit.server.enabled=false}.
 *
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "o11y.kit.server", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(O11yKitProperties.class)
public class ObservationWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ServerObservationHandler serverObservationHandler(HttpMetricRecorder recorder) {
        return new ServerObservationHandler(recorder);
    }

    @Bean
    public WebMvcConfigurer observationWebMvcConfigurer(
            ServerObservationHandler serverHandler,
            O11yKitProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                O11yKitProperties.Server server = properties.getServer();
                if (server.getMetrics().isEnabled()) {
                    registry.addInterceptor(serverHandler)
                            .addPathPatterns("/**")
                            .excludePathPatterns(server.getExcludePatterns()
                                    .toArray(new String[0]));
                }
            }
        };
    }
}
