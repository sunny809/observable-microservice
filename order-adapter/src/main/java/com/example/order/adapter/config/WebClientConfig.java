package com.example.order.adapter.config;

import io.o11y.kit.spring.webflux.ClientObservationHandler;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.ObjectProvider;
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
     * @param obsHandler client observation handler for metrics and tracing
     * @return configured WebClient instance
     */
    @Bean
    public WebClient inventoryWebClient(InventoryAdapterProperties properties,
                                        ObjectProvider<ClientObservationHandler> obsHandlerProvider) {
        return createWebClient(properties.getBaseUrl(), obsHandlerProvider.getIfAvailable());
    }

    /**
     * Creates a WebClient for WMS service calls with timeout configuration.
     *
     * @param properties WMS service base URL configuration
     * @param obsHandlerProvider client observation handler provider (may be absent in non-reactive apps)
     * @return configured WebClient instance
     */
    @Bean
    public WebClient wmsWebClient(WmsAdapterProperties properties,
                                  ObjectProvider<ClientObservationHandler> obsHandlerProvider) {
        return createWebClient(properties.getBaseUrl(), obsHandlerProvider.getIfAvailable());
    }

    /**
     * Creates a WebClient for TMS service calls with timeout configuration.
     *
     * @param properties TMS service base URL configuration
     * @param obsHandlerProvider client observation handler provider (may be absent in non-reactive apps)
     * @return configured WebClient instance
     */
    @Bean
    public WebClient tmsWebClient(TmsAdapterProperties properties,
                                  ObjectProvider<ClientObservationHandler> obsHandlerProvider) {
        return createWebClient(properties.getBaseUrl(), obsHandlerProvider.getIfAvailable());
    }

    private WebClient createWebClient(String baseUrl, ClientObservationHandler obsHandler) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (obsHandler != null) {
            builder.filter(obsHandler);
        }

        return builder.build();
    }
}