package com.order.demo.application.service;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderCancelledEvent;
import com.order.demo.application.domain.OrderNotFoundException;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.CancelOrderCommand;
import com.order.demo.application.port.in.OrderCancelledResult;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.out.CompensationLogPort;
import com.order.demo.application.port.out.CompensationStatus;
import com.order.demo.application.port.out.DomainEventPublisher;
import com.order.demo.application.port.out.InventoryPort;
import com.order.demo.application.port.out.MetricsPort;
import com.order.demo.application.port.out.OrderRepositoryPort;
import com.order.demo.application.port.out.OrderSnapshotPort;
import com.order.demo.application.port.out.SagaLogPort;
import com.order.demo.application.port.out.WmsPort;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class OrderCancellationSagaTest {

    private OrderRepositoryPort orderRepository;
    private InventoryPort inventoryPort;
    private WmsPort wmsPort;
    private SagaLogPort sagaLogPort;
    private DomainEventPublisher eventPublisher;
    private CompensationLogPort compensationLogPort;
    private OrderSnapshotPort orderSnapshotPort;
    private MetricsPort metricsPort;

    private OrderCancellationSaga saga;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepositoryPort.class);
        inventoryPort = mock(InventoryPort.class);
        wmsPort = mock(WmsPort.class);
        sagaLogPort = mock(SagaLogPort.class);
        eventPublisher = mock(DomainEventPublisher.class);
        compensationLogPort = mock(CompensationLogPort.class);
        orderSnapshotPort = mock(OrderSnapshotPort.class);
        metricsPort = mock(MetricsPort.class);
        saga = new OrderCancellationSaga(orderRepository, inventoryPort, wmsPort, sagaLogPort,
                eventPublisher, compensationLogPort, orderSnapshotPort, metricsPort);
    }

    private Order order(OrderStatus status, String customerId) {
        return new Order("ord-1", customerId, List.of(new OrderItem("SKU-1", 2)),
                status, "idem-1", "resv-1", Instant.now(), List.of("resv-1", "resv-2"), 1L);
    }

    private CancelOrderCommand command(String customerId) {
        return new CancelOrderCommand("ord-1", "customer changed mind", customerId);
    }

    @Test
    void cancel_releasesInventoryVoidsWmsAndMarksCancelled() {
        Order order = order(OrderStatus.WMS_ACKED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(inventoryPort.release(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(wmsPort.cancelInstruction(any(WmsShipmentInstruction.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderCancelledResult result = saga.cancel(command("cust-1"));

        assertEquals("ord-1", result.orderId());
        assertEquals(OrderStatus.CANCELLED, result.status());
        // both reservations released
        verify(inventoryPort, times(2)).release(anyString());
        // WMS voided because the order had reached WMS_ACKED
        verify(wmsPort).cancelInstruction(any(WmsShipmentInstruction.class));
        verify(orderRepository).updateStatus("ord-1", OrderStatus.CANCELLED);
        verify(orderSnapshotPort).saveSnapshot(any(Order.class), eq("CANCELLED"));
        verify(eventPublisher).publish(any(OrderCancelledEvent.class));
    }

    @Test
    void cancel_reservedOrderReleasesInventoryButDoesNotVoidWms() {
        Order order = order(OrderStatus.RESERVED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(inventoryPort.release(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        saga.cancel(command("cust-1"));

        verify(inventoryPort, times(2)).release(anyString());
        // WMS was never instructed (RESERVED), so no void call
        verifyNoInteractions(wmsPort);
        verify(orderRepository).updateStatus("ord-1", OrderStatus.CANCELLED);
    }

    @Test
    void cancel_notCancellableStateThrows() {
        Order order = order(OrderStatus.TMS_DISPATCHED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> saga.cancel(command("cust-1")));
        verifyNoInteractions(inventoryPort, wmsPort);
        verify(orderRepository, never()).updateStatus(anyString(), any());
    }

    @Test
    void cancel_wrongCustomerThrows() {
        Order order = order(OrderStatus.WMS_ACKED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> saga.cancel(command("cust-999")));
        verifyNoInteractions(inventoryPort, wmsPort);
        verify(orderRepository, never()).updateStatus(anyString(), any());
    }

    @Test
    void cancel_alreadyCancelledIsIdempotentNoOp() {
        Order order = order(OrderStatus.CANCELLED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        OrderCancelledResult result = saga.cancel(command("cust-1"));

        assertEquals(OrderStatus.CANCELLED, result.status());
        assertEquals("already cancelled", result.message());
        verifyNoInteractions(inventoryPort, wmsPort, orderSnapshotPort, eventPublisher);
        verify(orderRepository, never()).updateStatus(anyString(), any());
    }

    @Test
    void cancel_orderNotFoundThrows() {
        when(orderRepository.findById("ord-1")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> saga.cancel(command("cust-1")));
        verifyNoInteractions(inventoryPort, wmsPort);
    }

    @Test
    void cancel_releaseFailureStillMarksCancelledAndRecordsFailedCompensation() {
        Order order = order(OrderStatus.WMS_ACKED, "cust-1");
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(inventoryPort.release("resv-1"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("inventory down")));
        when(inventoryPort.release("resv-2"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(wmsPort.cancelInstruction(any())).thenReturn(CompletableFuture.completedFuture(null));

        OrderCancelledResult result = saga.cancel(command("cust-1"));

        assertEquals(OrderStatus.CANCELLED, result.status(), "release failure must not block cancellation");
        verify(orderRepository).updateStatus("ord-1", OrderStatus.CANCELLED);
        // resv-1 release failed → FAILED compensation recorded
        verify(compensationLogPort).save(
                eq("compensate:ord-1:CANCELLED:resv-1"), eq("ord-1"), eq("CANCELLED"), eq("resv-1"),
                eq(CompensationStatus.FAILED), anyString());
        // resv-2 release succeeded → COMPLETED compensation recorded
        verify(compensationLogPort).save(
                eq("compensate:ord-1:CANCELLED:resv-2"), eq("ord-1"), eq("CANCELLED"), eq("resv-2"),
                eq(CompensationStatus.COMPLETED), isNull());
    }
}
