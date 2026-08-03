package com.order.demo.adapter.outbound.tms;

import com.order.demo.application.port.out.TmsAck;
import com.order.demo.application.port.out.TmsPort;
import com.order.demo.application.port.out.TmsShipmentInstruction;
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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * REST adapter for sending dispatch instructions to the TMS service.
 *
 * <p>Implements the {@link TmsPort} outbound port using Spring WebClient
 * for asynchronous HTTP communication. The {@link #sendInstruction} method
 * returns a {@link CompletableFuture} that completes when the TMS responds.
 *
 * <p>Protected by Resilience4j circuit breaker and retry mechanisms.
 */
@Component
public class TmsRestAdapter implements TmsPort {

    private static final Logger log = LoggerFactory.getLogger(TmsRestAdapter.class);
    private final WebClient webClient;
    private final Tracer tracer;

    public TmsRestAdapter(@Qualifier("tmsWebClient") WebClient tmsWebClient) {
        this.webClient = tmsWebClient;
        this.tracer = TracerHelper.getTracer();
    }

    /**
     * Sends a dispatch instruction to the TMS service asynchronously.
     *
     * <p>The returned {@link CompletableFuture} completes when the TMS responds.
     * If the TMS rejects the instruction, the future completes normally with
     * {@code ack.isAccepted() == false}. If the HTTP call fails, the future
     * completes exceptionally.
     *
     * @param instruction the dispatch instruction containing order and reservation IDs
     * @return a future that completes with the TMS acknowledgment
     */
    @Override
    @CircuitBreaker(name = "tmsService", fallbackMethod = "handleTmsFallback")
    @Retry(name = "tmsService")
    public CompletableFuture<TmsAck> sendInstruction(TmsShipmentInstruction instruction) {
        Span span = tracer.spanBuilder(SpanNames.TMS_SEND).startSpan();
        try (Scope scope = span.makeCurrent()) {
            return webClient.post()
                    .uri("/api/tms/dispatches")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(instruction)
                    .retrieve()
                    .bodyToMono(TmsAckResponse.class)
                    .map(TmsAckResponse::toDomain)
                    .doOnError(ex -> {
                        span.recordException(ex);
                        log.error("Failed to send instruction to TMS", ex);
                    })
                    .doFinally(sig -> span.end())
                    .toFuture();
        }
    }

    /**
     * Circuit breaker fallback triggered when the TMS service is unavailable
     * or the circuit breaker is open.
     *
     * @param instruction the original dispatch instruction
     * @param throwable the cause of the failure
     * @return a failed future with a wrapped exception
     */
    public CompletableFuture<TmsAck> handleTmsFallback(TmsShipmentInstruction instruction, Throwable throwable) {
        log.warn("TMS fallback triggered for order {}", instruction.getOrderId(), throwable);
        return CompletableFuture.failedFuture(new RuntimeException("TMS service unavailable", throwable));
    }

    private static class TmsAckResponse {
        public boolean accepted;
        public String messageId;

        TmsAck toDomain() {
            return new TmsAck(accepted, messageId);
        }
    }
}
