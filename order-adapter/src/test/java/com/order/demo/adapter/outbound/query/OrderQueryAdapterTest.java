package com.order.demo.adapter.outbound.query;

import com.order.demo.application.port.in.OrderDetail;
import com.order.demo.application.port.in.OrderSearchCriteria;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.out.OrderSnapshotPort;
import com.order.demo.application.port.out.SagaLogEntry;
import com.order.demo.application.port.out.SagaLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class OrderQueryAdapterTest {

    private OrderViewJpaRepository viewRepository;
    private SagaLogPort sagaLogPort;
    private OrderSnapshotPort orderSnapshotPort;
    private OrderQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        viewRepository = mock(OrderViewJpaRepository.class);
        sagaLogPort = mock(SagaLogPort.class);
        orderSnapshotPort = mock(OrderSnapshotPort.class);
        adapter = new OrderQueryAdapter(viewRepository, sagaLogPort, orderSnapshotPort);
    }

    @Test
    void findDetailReturnsOrderWithSagaSteps() {
        OrderViewEntity entity = new OrderViewEntity();
        entity.setId("order-1");
        entity.setCustomerId("cust-1");
        entity.setStatus("CREATED");
        entity.setCreatedAt(LocalDateTime.now());
        when(viewRepository.findById("order-1")).thenReturn(Optional.of(entity));
        when(sagaLogPort.findByOrderId("order-1")).thenReturn(List.of(
                new SagaLogEntry(1L, "order-1", "ORDER_CREATED", "COMPLETED",
                        LocalDateTime.now(), LocalDateTime.now(), "Order created",
                        null, "CREATED", "SYSTEM")
        ));

        Optional<OrderDetail> result = adapter.findDetail("order-1");

        assertTrue(result.isPresent());
        verify(sagaLogPort).findByOrderId("order-1");
    }

    @Test
    void findDetailReturnsEmptyWhenNotFound() {
        when(viewRepository.findById("order-999")).thenReturn(Optional.empty());

        Optional<OrderDetail> result = adapter.findDetail("order-999");

        assertTrue(result.isEmpty());
    }

    @Test
    void searchReturnsPageOfOrderSummaries() {
        OrderViewEntity entity = new OrderViewEntity();
        entity.setId("order-1");
        entity.setCustomerId("cust-1");
        entity.setStatus("CREATED");
        entity.setCreatedAt(LocalDateTime.now());
        Page<OrderViewEntity> entityPage = new PageImpl<>(List.of(entity));
        when(viewRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(entityPage);

        OrderSearchCriteria criteria = new OrderSearchCriteria("cust-1", null, null, null, 0, 20);
        Page<OrderSummary> result = adapter.search(criteria);

        assertNotNull(result);
        verify(viewRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class));
    }

    @Test
    void searchWithNoFiltersReturnsAll() {
        Page<OrderViewEntity> emptyPage = new PageImpl<>(List.of());
        when(viewRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(emptyPage);

        OrderSearchCriteria criteria = new OrderSearchCriteria(null, null, null, null, 0, 20);
        Page<OrderSummary> result = adapter.search(criteria);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void findOrderAtDelegatesToSnapshotPort() {
        Instant pointInTime = Instant.parse("2026-08-01T16:00:00Z");
        OrderSummary summary = new OrderSummary("order-1", null, "WMS_ACKED",
                Instant.parse("2026-08-01T15:00:00Z"), 0, 0, null, "WMS_ACKED");
        when(orderSnapshotPort.findSnapshotAt("order-1", pointInTime)).thenReturn(Optional.of(summary));

        Optional<OrderSummary> result = adapter.findOrderAt("order-1", pointInTime);

        assertTrue(result.isPresent());
        assertEquals("WMS_ACKED", result.get().status());
        verify(orderSnapshotPort).findSnapshotAt("order-1", pointInTime);
    }

    @Test
    void findOrderAtReturnsEmptyWhenNoSnapshot() {
        Instant pointInTime = Instant.parse("2026-08-01T16:00:00Z");
        when(orderSnapshotPort.findSnapshotAt("order-1", pointInTime)).thenReturn(Optional.empty());

        Optional<OrderSummary> result = adapter.findOrderAt("order-1", pointInTime);

        assertTrue(result.isEmpty());
    }
}
