package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.WmsPickingCompletedEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.OrderRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmsCallbackControllerTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;

    private WmsCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new WmsCallbackController(orderRepository, eventPublisher);
    }

    @Test
    void shouldReturn200AndPublishEventWhenOrderIsWmsAcked() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.WMS_ACKED, "idem-1", "resv-1", Instant.now(),
                List.of("resv-1"));
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        ResponseEntity<Map<String, String>> response = controller.onPickingCompleted(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "accepted");

        ArgumentCaptor<WmsPickingCompletedEvent> captor =
                ArgumentCaptor.forClass(WmsPickingCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("ord-1");
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("non-existent");

        assertThatThrownBy(() -> controller.onPickingCompleted(request))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("non-existent");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReturn409WhenOrderIsNotWmsAcked() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", "resv-1", Instant.now());
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        ResponseEntity<Map<String, String>> response = controller.onPickingCompleted(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "invalid_order_status");
        assertThat(response.getBody()).containsEntry("current", "CREATED");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRebuildReservationsWithAllReservationIds() {
        Order order = new Order("ord-1", "cust-1",
                List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 3)),
                OrderStatus.WMS_ACKED, "idem-1", "resv-1", Instant.now(),
                List.of("resv-1", "resv-2"));
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        controller.onPickingCompleted(request);

        ArgumentCaptor<WmsPickingCompletedEvent> captor =
                ArgumentCaptor.forClass(WmsPickingCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());

        List<InventoryReservation> reservations = captor.getValue().getReservations();
        assertThat(reservations).hasSize(2);
        assertThat(reservations.get(0).getReservationId()).isEqualTo("resv-1");
        assertThat(reservations.get(1).getReservationId()).isEqualTo("resv-2");
        assertThat(reservations.get(0).getSku()).isEqualTo("SKU-1");
        assertThat(reservations.get(1).getSku()).isEqualTo("SKU-2");
    }
}
