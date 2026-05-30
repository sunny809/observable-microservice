package com.example.order.adapter.inbound.rest;

import com.example.order.adapter.inbound.rest.aop.Traced;
import com.example.order.o11y.util.TracerHelper.SpanNames;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.PlaceOrderCommand;
import com.example.order.application.port.in.PlaceOrderUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;

    public OrderController(PlaceOrderUseCase placeOrderUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
    }

    @PostMapping
    @Traced(spanName = SpanNames.ORDER_PLACEMENT)
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest servletRequest) {
        String traceId = MDC.get("traceId");

        PlaceOrderCommand command = new PlaceOrderCommand(
                request.getCustomerId(),
                toOrderItems(request.getItems()),
                request.getIdempotencyKey());

        var result = placeOrderUseCase.placeOrder(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OrderResponse(result.getOrderId(), result.getStatus(), traceId));
    }

    private List<OrderItem> toOrderItems(List<PlaceOrderRequest.OrderItemRequest> requestItems) {
        return requestItems.stream()
                .map(item -> new OrderItem(item.getSku(), item.getQuantity()))
                .collect(Collectors.toList());
    }
}
