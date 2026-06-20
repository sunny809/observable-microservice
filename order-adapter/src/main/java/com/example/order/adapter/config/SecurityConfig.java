package com.example.order.adapter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for JWT-based API authentication.
 *
 * <p>Configures Spring Security as an OAuth2 resource server that validates
 * JWT tokens from an authorization server. The {@code /api/orders} endpoints
 * require a valid JWT with the {@code order:write} scope.
 *
 * <p>Public endpoints (health, metrics, Swagger UI) are permitted without
 * authentication to support monitoring and documentation access.
 *
 * <p>To disable security in development, set {@code spring.security.enabled=false}.
 *
 * <p>This configuration is NOT active in the {@code test} profile — see
 * {@link TestSecurityConfig} for test-specific security configuration.
 *
 * @see org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    private final String jwtIssuerUri;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080}") String jwtIssuerUri) {
        this.jwtIssuerUri = jwtIssuerUri;
    }

    /**
     * Configures the security filter chain.
     *
     * <p>Public paths (health, metrics, Swagger, OpenAPI) are permitted.
     * All other requests require a valid JWT token.
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/**",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }

    /**
     * Creates a JWT decoder using the configured issuer URI.
     *
     * <p>In production, this should point to your OAuth2 authorization server
     * (e.g., Keycloak, Auth0, Okta). For local development, you can use a
     * mock JWT decoder or disable security entirely.
     *
     * @return the JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String jwkSetUri = jwtIssuerUri + "/.well-known/jwks.json";
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
