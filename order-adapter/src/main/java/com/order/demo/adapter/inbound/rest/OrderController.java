package com.order.demo.adapter.inbound.rest;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.order.demo.adapter.inbound.rest.aop.Traced;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.in.PlaceOrderCommand;
import com.order.demo.application.port.in.PlaceOrderUseCase;
import com.order.demo.application.port.out.OrderQueryPort;
import com.order.demo.adapter.observability.TracerHelper.SpanNames;

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
    private final OrderQueryPort orderQueryPort;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, OrderQueryPort orderQueryPort) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.orderQueryPort = orderQueryPort;
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

    /**
     * Finds orders containing a specific SKU.
     */
    @GetMapping("/by-sku")
    @Operation(summary = "Find orders by SKU", description = "Finds all orders containing an item with the given SKU")
    public List<OrderSummary> findBySku(@RequestParam("sku") String sku) {
        return orderQueryPort.findBySku(sku);
    }

    /**
     * Retrieves order state at a specific point in time.
     */
    @GetMapping("/{orderId}/at")
    @Operation(summary = "Get order state at time",
               description = "Retrieves the order state at a specific point in time using snapshots")
    public ResponseEntity<OrderSummary> getOrderAt(
            @PathVariable("orderId") String orderId,
            @RequestParam("time") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant time) {
        return orderQueryPort.findOrderAt(orderId, time)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
