package com.order.demo.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for local development that disables JWT authentication.
 *
 * <p>Active only when the {@code local} Spring profile is enabled.
 * All endpoints are permitted without authentication for ease of local testing.
 *
 * <p>To use: {@code SPRING_PROFILES_ACTIVE=local make run}
 *
 * @see SecurityConfig for production JWT configuration
 */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

    /**
     * Configures a permissive security filter chain for local development.
     *
     * <p>All requests are permitted without authentication. CSRF is disabled
     * for stateless API testing.
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
            );

        return http.build();
    }
}
