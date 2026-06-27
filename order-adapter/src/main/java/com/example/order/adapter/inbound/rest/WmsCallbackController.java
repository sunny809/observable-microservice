package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.WmsPickingCompletedEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.OrderRepositoryPort;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/wms/callback")
public class WmsCallbackController {

    private final OrderRepositoryPort orderRepository;
    private final DomainEventPublisher eventPublisher;

    public WmsCallbackController(OrderRepositoryPort orderRepository,
                                  DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/picking-completed")
    public ResponseEntity<Map<String, String>> onPickingCompleted(
            @Valid @RequestBody WmsCallbackRequest request) {

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        if (order.getStatus() != OrderStatus.WMS_ACKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "invalid_order_status",
                    "current", order.getStatus().name(),
                    "expected", OrderStatus.WMS_ACKED.name()
            ));
        }

        List<InventoryReservation> reservations = rebuildReservations(order);

        eventPublisher.publish(new WmsPickingCompletedEvent(
                order.getOrderId(),
                order.getReservationId(),
                reservations));

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    private List<InventoryReservation> rebuildReservations(Order order) {
        List<OrderItem> items = order.getItems();
        List<String> reservationIds = order.getAllReservationIds();

        if (reservationIds.size() != items.size()) {
            throw new IllegalStateException(
                    "Reservation ID count mismatch: items=" + items.size() +
                    ", reservationIds=" + reservationIds.size() +
                    " for order " + order.getOrderId());
        }

        List<InventoryReservation> reservations = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            reservations.add(InventoryReservation.withId(
                    reservationIds.get(i), item.getSku(), item.getQuantity(), order.getOrderId()));
        }
        return reservations;
    }
}
