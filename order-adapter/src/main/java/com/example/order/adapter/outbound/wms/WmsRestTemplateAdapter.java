package com.order.demo.adapter.outbound.wms;

import com.order.demo.adapter.config.WmsAdapterProperties;
import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsPort;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * Synchronous REST adapter for sending shipment instructions to the WMS service.
 *
 * <p>Implements the {@link WmsPort} outbound port using Spring {@link RestTemplate}
 * for synchronous (blocking) HTTP communication. This demonstrates the o11y-kit
 * sync client observation path: the {@code RestTemplateObservationInterceptor} is
 * automatically added by the o11y-kit {@code RestTemplateCustomizer}, producing
 * the same HTTP client metric series ({@code http.client.requests},
 * {@code http.client.errors}) as the WebClient-based adapters.
 *
 * <p><strong>Comparison with {@link WmsRestAdapter}:</strong> The existing
 * {@code WmsRestAdapter} uses WebClient (reactive, async). This adapter uses
 * RestTemplate (blocking, sync). Both produce identical o11y metrics, but the
 * sync path is simpler to reason about and often preferred for internal
 * service-to-service calls where reactive pipelines are not needed.
 *
 * <p><strong>Observation:</strong> No manual span management is needed here.
 * The {@code RestTemplateObservationInterceptor} handles metric recording and
 * optional OTel span creation automatically for every outbound call.
 *
 * @see WmsRestAdapter for the WebClient-based (async) implementation
 * @see com.order.demo.adapter.config.RestTemplateConfig
 */
@Component
public class WmsRestTemplateAdapter implements WmsPort {

    private static final Logger log = LoggerFactory.getLogger(WmsRestTemplateAdapter.class);

    private final RestTemplate restTemplate;

    /**
     * Creates a new WmsRestTemplateAdapter.
     *
     * @param wmsRestTemplate the RestTemplate configured with the WMS base URL
     *                        and o11y-kit observation interceptor
     */
    public WmsRestTemplateAdapter(@Qualifier("wmsRestTemplate") RestTemplate wmsRestTemplate) {
        this.restTemplate = wmsRestTemplate;
    }

    /**
     * Sends a shipment instruction to the WMS service synchronously.
     *
     * <p>The call is wrapped in a {@link CompletableFuture} to satisfy the
     * {@link WmsPort} contract, but the actual HTTP call blocks the calling
     * thread. The o11y-kit interceptor records metrics and tracing automatically.
     *
     * <p>If the WMS returns a non-2xx response, {@link RestTemplate} throws
     * a {@code RestClientException}, which triggers the circuit breaker
     * fallback.
     *
     * @param instruction the shipment instruction containing order and reservation IDs
     * @return a future that completes with the WMS acknowledgment
     */
    @Override
    @CircuitBreaker(name = "wmsService", fallbackMethod = "handleWmsFallback")
    @Retry(name = "wmsService")
    public CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction) {
        try {
            ResponseEntity<WmsAckResponse> response = restTemplate.postForEntity(
                    "/api/wms/shipments",
                    instruction,
                    WmsAckResponse.class
            );

            WmsAckResponse body = response.getBody();
            if (body == null) {
                log.warn("WMS returned null body for order {}", instruction.getOrderId());
                return CompletableFuture.completedFuture(new WmsAck(false, null));
            }

            return CompletableFuture.completedFuture(body.toDomain());
        } catch (Exception ex) {
            log.error("Failed to send instruction to WMS for order {}", instruction.getOrderId(), ex);
            CompletableFuture<WmsAck> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    /**
     * Circuit breaker fallback triggered when the WMS service is unavailable
     * or the circuit breaker is open.
     *
     * @param instruction the original shipment instruction
     * @param throwable   the cause of the failure
     * @return a failed future with a wrapped exception
     */
    public CompletableFuture<WmsAck> handleWmsFallback(WmsShipmentInstruction instruction,
                                                       Throwable throwable) {
        log.warn("WMS RestTemplate fallback triggered for order {}", instruction.getOrderId(), throwable);
        CompletableFuture<WmsAck> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("WMS service unavailable (RestTemplate)", throwable));
        return failed;
    }
}
