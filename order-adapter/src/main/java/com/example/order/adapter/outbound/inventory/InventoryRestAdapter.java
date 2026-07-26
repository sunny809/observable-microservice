package com.order.demo.adapter.outbound.inventory;

import java.util.concurrent.CompletableFuture;

import com.order.demo.adapter.config.InventoryAdapterProperties;
import com.order.demo.application.domain.InventoryReservation;
import com.order.demo.application.domain.ReservationStatus;
import com.order.demo.application.port.out.InventoryPort;
import com.order.demo.application.port.out.ReservationRequest;
import com.order.demo.application.port.out.ConfirmReservationCommand;
import com.order.demo.o11y.util.TracerHelper;
import com.order.demo.o11y.util.TracerHelper.SpanNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST adapter for inventory service operations.
 *
 * <p>Implements the {@link InventoryPort} outbound port using Spring WebClient
 * for HTTP communication. All operations are synchronous (blocking) and protected
 * by Resilience4j circuit breakers and retry mechanisms.
 */
@Component
public class InventoryRestAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestAdapter.class);
    private final WebClient webClient;
    private final Tracer tracer;

    public InventoryRestAdapter(@Qualifier("inventoryWebClient") WebClient inventoryWebClient) {
        this.webClient = inventoryWebClient;
        this.tracer = TracerHelper.getTracer();
    }

    /**
     * Reserves inventory for an order item.
     *
     * <p>Returns {@code null} if the inventory service responds with
     * {@code success: false}, indicating insufficient stock.
     *
     * @param request the reservation request containing SKU, quantity, and order ID
     * @return the reservation details, or {@code null} if reservation fails
     * @throws RuntimeException if the HTTP call fails or the circuit breaker is open
     */
    @Override
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "handleOccupyFallback")
    @Retry(name = "inventoryService")
    public CompletableFuture<InventoryReservation> occupy(ReservationRequest request) {
        Span span = tracer.spanBuilder(SpanNames.INVENTORY_OCCUPY).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return webClient.post()
                    .uri("/api/inventory/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(InventoryApiResponse.class)
                    .flatMap(response -> {
                        if (response == null || !response.success) {
                            span.setAttribute("inventory.reserve.success", false);
                            return reactor.core.publisher.Mono.justOrEmpty((InventoryReservation) null);
                        }
                        span.setAttribute("inventory.reserve.success", true);
                        return reactor.core.publisher.Mono.just(new InventoryReservation(response.reservationId, request.getSku(), request.getQuantity(), request.getOrderId(), ReservationStatus.PENDING, java.time.Instant.now(), null));
                    })
                    .doOnError(ex -> {
                        span.recordException(ex);
                        log.error("Failed to reserve inventory", ex);
                    })
                    .doFinally(sig -> span.end())
                    .toFuture();
        }
    }

    public CompletableFuture<InventoryReservation> handleOccupyFallback(ReservationRequest request, Throwable throwable) {
        log.warn("Inventory occupy fallback for order {}", request.getOrderId(), throwable);
        return CompletableFuture.failedFuture(new RuntimeException("Inventory service unavailable", throwable));
    }

    /**
     * Releases a previously reserved inventory item.
     *
     * @param reservationId the ID of the reservation to release
     * @throws RuntimeException if the HTTP call fails or the circuit breaker is open
     */
    @Override
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "handleReleaseFallback")
    @Retry(name = "inventoryService")
    public CompletableFuture<Void> release(String reservationId) {
        Span span = tracer.spanBuilder(SpanNames.INVENTORY_RELEASE).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return webClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/api/inventory/reserve/{reservationId}").build(reservationId))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnError(ex -> {
                        span.recordException(ex);
                        log.error("Failed to release inventory", ex);
                    })
                    .doFinally(sig -> span.end())
                    .toFuture();
        }
    }

    public CompletableFuture<Void> handleReleaseFallback(String reservationId, Throwable throwable) {
        log.warn("Release fallback for reservation {}", reservationId, throwable);
        return CompletableFuture.failedFuture(new RuntimeException("Inventory release failed", throwable));
    }

    /**
     * Confirms a previously reserved inventory item.
     *
     * @param request the confirmation command containing the reservation ID
     * @throws RuntimeException if the HTTP call fails or the circuit breaker is open
     */
    @Override
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "handleConfirmFallback")
    @Retry(name = "inventoryService")
    public CompletableFuture<Void> confirm(ConfirmReservationCommand request) {
        Span span = tracer.spanBuilder(SpanNames.INVENTORY_CONFIRM).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return webClient.post()
                    .uri("/api/inventory/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnError(ex -> {
                        span.recordException(ex);
                        log.error("Failed to confirm inventory", ex);
                    })
                    .doFinally(sig -> span.end())
                    .toFuture();
        }
    }

    public CompletableFuture<Void> handleConfirmFallback(ConfirmReservationCommand request, Throwable throwable) {
        log.warn("Inventory confirm fallback for reservation {}", request.getReservationId(), throwable);
        return CompletableFuture.failedFuture(new RuntimeException("Inventory confirmation failed", throwable));
    }

    private static class InventoryApiResponse {
        public boolean success;
        public String reservationId;
    }
}
