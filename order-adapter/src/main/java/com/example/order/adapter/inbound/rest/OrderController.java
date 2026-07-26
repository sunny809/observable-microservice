package com.order.demo.adapter.inbound.rest;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.demo.adapter.inbound.rest.aop.Traced;
import com.order.demo.application.port.in.PlaceOrderCommand;
import com.order.demo.application.port.in.PlaceOrderUseCase;
import com.order.demo.o11y.util.TracerHelper.SpanNames;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for order placement operations.
 *
 * <p>Exposes the {@link PlaceOrderUseCase} inbound port over HTTP.
 * All endpoints are traced with OpenTelemetry for distributed tracing.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Orders", description = "Order placement and management APIs (v1)")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;

    public OrderController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    /**
     * Places a new order.
     *
     * <p>Validates the request, triggers the order placement saga,
     * and returns the created order with a Location header.
     */
    @PostMapping
    @Traced(spanName = SpanNames.ORDER_PLACEMENT)
    @PreAuthorize("hasAuthority('SCOPE_order:write')")
    @RateLimiter(name = "orderPlacement")
    @Operation(
            summary = "Place a new order",
            description = "Creates a new order after validating idempotency, reserving inventory, " +
                    "and persisting the order. The WMS instruction is sent asynchronously."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error - invalid request body"),
            @ApiResponse(responseCode = "409", description = "Duplicate order - same idempotency key already exists"),
            @ApiResponse(responseCode = "422", description = "Insufficient inventory for one or more items"),
            @ApiResponse(responseCode = "500", description = "Service unavailable - circuit breaker open or downstream failure")
    })
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest servletRequest) {
        String correlationId = MDC.get("traceId");

        PlaceOrderCommand command = PlaceOrderMapper.toCommand(request);

        var result = placeOrderUseCase.placeOrder(command);

        var response = new OrderResponse(result.getOrderId(), result.getStatus(), correlationId);
        URI location = URI.create("/api/v1/orders/" + result.getOrderId());
        return ResponseEntity.created(location).body(response);
    }

}
