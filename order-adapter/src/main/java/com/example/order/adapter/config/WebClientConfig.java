package com.example.order.adapter.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration with proper timeout settings.
 *
 * <p>Provides pre-configured WebClient instances for external service calls
 * with connection, read, and write timeouts to prevent resource exhaustion
 * under high load.
 */
@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int RESPONSE_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 10;
    private static final int WRITE_TIMEOUT_SECONDS = 10;

    /**
     * Creates a WebClient for inventory service calls with timeout configuration.
     *
     * @param properties inventory service base URL configuration
     * @return configured WebClient instance
     */
    @Bean
    public WebClient inventoryWebClient(InventoryAdapterProperties properties) {
        return createWebClient(properties.getBaseUrl());
    }

    /**
     * Creates a WebClient for WMS service calls with timeout configuration.
     *
     * @param properties WMS service base URL configuration
     * @return configured WebClient instance
     */
    @Bean
    public WebClient wmsWebClient(WmsAdapterProperties properties) {
        return createWebClient(properties.getBaseUrl());
    }

    private WebClient createWebClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}