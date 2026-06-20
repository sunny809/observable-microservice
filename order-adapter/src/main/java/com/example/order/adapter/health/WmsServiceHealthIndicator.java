package com.example.order.adapter.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Health indicator that checks connectivity to the WMS (Warehouse Management System) service.
 *
 * <p>Reports {@code UP} if the WMS service health endpoint responds
 * successfully, or {@code DOWN} if the service is unreachable. This indicator
 * is used by Kubernetes readiness probes to determine if the pod should
 * receive traffic.
 *
 * @see org.springframework.boot.actuate.health.HealthIndicator
 */
@Component
public class WmsServiceHealthIndicator implements HealthIndicator {

    private final WebClient webClient;

    public WmsServiceHealthIndicator(@Qualifier("wmsWebClient") WebClient wmsWebClient) {
        this.webClient = wmsWebClient;
    }

    @Override
    public Health health() {
        try {
            // Attempt a lightweight health check against the WMS service
            webClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(java.time.Duration.ofSeconds(3));

            return Health.up()
                    .withDetail("service", "wms-service")
                    .withDetail("status", "reachable")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("service", "wms-service")
                    .withDetail("status", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
