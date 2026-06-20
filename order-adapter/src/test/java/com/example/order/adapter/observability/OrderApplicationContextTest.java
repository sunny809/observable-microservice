package com.example.order.adapter.observability;

import io.o11y.kit.autoconfigure.O11yKitProperties;
import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.spring.webmvc.ServerObservationHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the host application (order-demo's adapter) wires the
 * o11y-kit auto-configuration correctly when running as a regular Spring Boot
 * application — i.e. that {@link HttpMetricRecorder} and
 * {@link ServerObservationHandler} beans are present in the running context
 * and that the {@code o11y.kit.client.enabled=false} property does not break
 * startup.
 *
 * <p>The test boots a minimal {@link TestApp} rather than the full
 * {@code OrderServiceApplication} so it does not require a database, Kafka,
 * or any other external dependency. {@code TestApp} re-uses the production
 * {@code com.example.order.adapter.observability} package only as the
 * component-scan root, while excluding the heavy auto-configurations that
 * the production app pulls in via its own {@code @SpringBootApplication}.
 *
 * <p>Note: the original task description called for
 * {@code WebEnvironment.NONE}; we use {@link SpringBootTest.WebEnvironment#MOCK}
 * because {@link ServerObservationHandler} is gated on a servlet web context
 * via {@code @ConditionalOnWebApplication(type = SERVLET)} and would not be
 * created under {@code NONE}. {@code MOCK} provides a servlet web
 * {@code ApplicationContext} without binding a real port and so still
 * satisfies the "no external dependencies" requirement.
 */
@Tag("integration")
@SpringBootTest(
        classes = OrderApplicationContextTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "o11y.kit.client.enabled=false",
        "spring.main.banner-mode=off",
        // Override the exclusions inherited from src/test/resources/application.yml
        // — for this test we DO want WebMvc auto-config so the servlet web context
        // (and our ServerObservationHandler) wire up correctly.
        "spring.autoconfigure.exclude="
})
class OrderApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private O11yKitProperties properties;

    @Test
    void contextStartsWithO11yKitBeansWired() {
        assertThat(context.getBean(HttpMetricRecorder.class)).isNotNull();
        assertThat(context.getBean(ServerObservationHandler.class)).isNotNull();
    }

    @Test
    void clientDisabledPropertyDoesNotPreventStartup() {
        // The gate is not yet enforced at bean creation, but the property must bind
        // without breaking application startup.
        assertThat(properties.getClient().isEnabled()).isFalse();
        assertThat(properties.getClient().getMetrics().isEnabled()).isTrue();
    }

    /**
     * Minimal Spring Boot application used only by this test. Scopes
     * component scanning to this test's own package (no production beans are
     * picked up) and excludes infrastructure auto-configurations so the
     * context can boot without any external dependency. The o11y-kit
     * auto-configurations are loaded normally via
     * {@code spring.factories} / {@code AutoConfiguration.imports}.
     */
    @SpringBootApplication(
            exclude = {
                    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class,
                    org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
            })
    static class TestApp {

        @org.springframework.context.annotation.Bean
        io.micrometer.core.instrument.MeterRegistry testMeterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }
    }
}
