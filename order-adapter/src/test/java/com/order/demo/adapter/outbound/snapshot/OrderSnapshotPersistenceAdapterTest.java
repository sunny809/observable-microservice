package com.order.demo.adapter.outbound.snapshot;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.in.OrderSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OrderSnapshotPersistenceAdapterTest {

    @Mock
    private OrderSnapshotJpaRepository repository;

    private OrderSnapshotPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrderSnapshotPersistenceAdapter(repository);
    }

    @Test
    @DisplayName("saveSnapshot persists entity with correct fields")
    void saveSnapshot_persistsEntity() {
        Order order = new Order(
                "order-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", null,
                Instant.now(), List.of(), 1L
        );

        adapter.saveSnapshot(order, "ORDER_CREATED");

        ArgumentCaptor<OrderSnapshotEntity> captor = ArgumentCaptor.forClass(OrderSnapshotEntity.class);
        verify(repository).save(captor.capture());

        OrderSnapshotEntity saved = captor.getValue();
        assertEquals("order-1", saved.getOrderId());
        assertEquals("CREATED", saved.getStatus());
        assertEquals("ORDER_CREATED", saved.getReason());
        assertEquals(1L, saved.getVersion());
        assertNotNull(saved.getSnapshot());
    }

    @Test
    @DisplayName("findSnapshotAt returns summary when snapshot exists")
    void findSnapshotAt_returnsSummary() {
        OrderSnapshotEntity entity = new OrderSnapshotEntity();
        entity.setOrderId("order-1");
        entity.setStatus("WMS_ACKED");
        entity.setReason("WMS_ACKED");
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 1, 15, 0));
        entity.setSnapshot("{\"orderId\":\"order-1\",\"customerId\":\"cust-1\"," +
                "\"items\":[{\"sku\":\"SKU-1\",\"quantity\":5}]," +
                "\"allReservationIds\":[\"res-1\",\"res-2\"]}");

        when(repository.findLatestSnapshotAt(eq("order-1"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(entity));

        Optional<OrderSummary> result = adapter.findSnapshotAt("order-1",
                Instant.parse("2026-08-01T16:00:00Z"));

        assertTrue(result.isPresent());
        assertEquals("WMS_ACKED", result.get().status());
        assertEquals("cust-1", result.get().customerId());
        assertEquals(5, result.get().totalQuantity());
        assertEquals(2, result.get().reservationCount());
    }

    @Test
    @DisplayName("findSnapshotAt returns empty when no snapshot exists")
    void findSnapshotAt_returnsEmpty() {
        when(repository.findLatestSnapshotAt(eq("order-1"), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        Optional<OrderSummary> result = adapter.findSnapshotAt("order-1",
                Instant.parse("2026-08-01T16:00:00Z"));

        assertTrue(result.isEmpty());
    }
}
