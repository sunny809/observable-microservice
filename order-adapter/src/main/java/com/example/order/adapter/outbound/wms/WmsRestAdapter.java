package com.order.demo.adapter.outbound.wms;

import com.order.demo.adapter.config.WmsAdapterProperties;
import com.order.demo.application.port.out.WmsAck;
import com.order.demo.application.port.out.WmsPort;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import com.order.demo.adapter.observability.TracerHelper;
import com.order.demo.adapter.observability.TracerHelper.SpanNames;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST adapter for sending shipment instructions to the WMS service.
 *
 * <p>Implements the {@link WmsPort} outbound port using Spring WebClient
 * for asynchronous HTTP communication. The {@link #sendInstruction} method
 * returns a {@link CompletableFuture} that completes when the WMS responds.
 *
 * <p>Protected by Resilience4j circuit breaker and retry mechanisms.
 */
@Component
@Primary
public class WmsRestAdapter implements WmsPort {

    private static final Logger log = LoggerFactory.getLogger(WmsRestAdapter.class);
    private final WebClient webClient;
    private final Tracer tracer;

    public WmsRestAdapter(@Qualifier("wmsWebClient") WebClient wmsWebClient) {
        this.webClient = wmsWebClient;
        this.tracer = TracerHelper.getTracer();
    }

    /**
     * Sends a shipment instruction to the WMS service asynchronously.
     *
     * <p>The returned {@link CompletableFuture} completes when the WMS responds.
     * If the WMS rejects the instruction, the future completes normally with
     * {@code ack.isAccepted() == false}. If the HTTP call fails, the future
     * completes exceptionally.
     *
     * @param instruction the shipment instruction containing order and reservation IDs
     * @return a future that completes with the WMS acknowledgment
     */
    @Override
    @CircuitBreaker(name = "wmsService", fallbackMethod = "handleWmsFallback")
    @Retry(name = "wmsService")
    public CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction) {
        Span span = tracer.spanBuilder(SpanNames.WMS_SEND).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return webClient.post()
                    .uri("/api/wms/shipments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(instruction)
                    .retrieve()
                    .bodyToMono(WmsAckResponse.class)
                    .map(WmsAckResponse::toDomain)
                    .doOnError(ex -> {
                        span.recordException(ex);
                        log.error("Failed to send instruction to WMS", ex);
                    })
                    .doFinally(sig -> span.end())
                    .toFuture();
        }
    }

    /**
     * Circuit breaker fallback triggered when the WMS service is unavailable
     * or the circuit breaker is open.
     *
     * @param instruction the original shipment instruction
     * @param throwable the cause of the failure
     * @return a failed future with a wrapped exception
     */
    public CompletableFuture<WmsAck> handleWmsFallback(WmsShipmentInstruction instruction, Throwable throwable) {
        log.warn("WMS fallback triggered for order {}", instruction.getOrderId(), throwable);
        return CompletableFuture.failedFuture(new RuntimeException("WMS service unavailable", throwable));
    }
}