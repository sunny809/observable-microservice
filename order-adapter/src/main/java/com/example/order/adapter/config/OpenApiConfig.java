package com.example.order.adapter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAPI (Swagger) documentation.
 *
 * <p>Configures the OpenAPI 3.0 specification with project metadata,
 * contact information, and license details. The spec is served at
 * {@code /v3/api-docs} and the Swagger UI at {@code /swagger-ui.html}.
 *
 * @see <a href="https://springdoc.org/">SpringDoc OpenAPI</a>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the OpenAPI specification bean.
     *
     * @return the configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("Production-ready Spring Boot order service demonstrating hexagonal architecture, " +
                                "Saga pattern, circuit breakers, and distributed tracing.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Order Service Team")
                                .email("team@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                        .url("/api/v1")
                        .description("Version 1 API"));
    }
}
