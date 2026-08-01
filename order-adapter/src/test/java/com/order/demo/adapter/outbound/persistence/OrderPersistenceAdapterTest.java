package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.OrderItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPersistenceAdapterTest {

    private final OrderJpaRepository repository = mock(OrderJpaRepository.class);
    private final OrderPersistenceAdapter adapter = new OrderPersistenceAdapter(repository);

    @Test
    void testSaveCallsRepositoryWithConvertedEntity() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", "resv-1", java.time.Instant.now());

        adapter.save(order);

        verify(repository).save(any(OrderEntity.class));
    }

    @Test
    void testSaveSerializesItems() {
        Order order = new Order("ord-1", "cust-1", List.of(
                new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 3)),
                OrderStatus.CREATED, "idem-1", "resv-1", java.time.Instant.now());

        adapter.save(order);

        verify(repository).save(argThat(entity ->
                entity.getItems() != null && entity.getItems().contains("SKU-1") && entity.getItems().contains("SKU-2")));
    }

    @Test
    void testFindByIdReturnsDomainObjectWithDeserializedItems() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems("[{\"sku\":\"SKU-1\",\"quantity\":2}]");
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findById("ord-1");

        assertTrue(result.isPresent());
        assertEquals("ord-1", result.get().getOrderId());
        assertEquals("cust-1", result.get().getCustomerId());
        assertEquals(OrderStatus.CREATED, result.get().getStatus());
        assertEquals("resv-1", result.get().getReservationId());
        assertEquals(1, result.get().getItems().size());
        assertEquals("SKU-1", result.get().getItems().get(0).getSku());
        assertEquals(2, result.get().getItems().get(0).getQuantity());
    }

    @Test
    void testFindByIdReturnsEmptyWhenNotFound() {
        when(repository.findById("ord-999")).thenReturn(Optional.empty());

        Optional<Order> result = adapter.findById("ord-999");

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindByIdempotencyKeyReturnsDomainObject() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-key-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems("[]");
        when(repository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findByIdempotencyKey("idem-key-1");

        assertTrue(result.isPresent());
        assertEquals("ord-1", result.get().getOrderId());
    }

    @Test
    void testUpdateStatusDelegatesToJpqlUpdate() {
        adapter.updateStatus("ord-1", OrderStatus.WMS_ACKED);

        verify(repository).updateStatus("ord-1", "WMS_ACKED");
    }

    @Test
    void testFindByIdWithNullItemsReturnsEmptyList() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems(null);
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findById("ord-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().getItems().isEmpty());
    }

    @Test
    void testFindByIdWithBlankItemsReturnsEmptyList() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems("");
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findById("ord-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().getItems().isEmpty());
    }

    @Test
    void testFindByIdWithInvalidItemsJsonThrowsDataIntegrityException() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-1",
                "CREATED", LocalDateTime.now());
        entity.setItems("not-json");
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> adapter.findById("ord-1"));
        assertTrue(ex.getMessage().contains("data corruption"));
    }

    @Test
    void testSaveSerializesReservationIds() {
        List<String> resvIds = List.of("resv-abc", "resv-def");
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", "resv-abc", java.time.Instant.now(), resvIds);

        adapter.save(order);

        verify(repository).save(argThat(entity ->
                entity.getReservationIds() != null &&
                entity.getReservationIds().contains("resv-abc") &&
                entity.getReservationIds().contains("resv-def")));
    }

    @Test
    void testFindByIdDeserializesReservationIds() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-abc",
                "CREATED", LocalDateTime.now());
        entity.setItems("[]");
        entity.setReservationIds("[\"resv-abc\",\"resv-def\"]");
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findById("ord-1");

        assertTrue(result.isPresent());
        assertEquals(List.of("resv-abc", "resv-def"), result.get().getAllReservationIds());
    }

    @Test
    void testFindByIdReturnsEmptyReservationIdsWhenNull() {
        OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-abc",
                "CREATED", LocalDateTime.now());
        entity.setItems("[]");
        entity.setReservationIds(null);
        when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

        Optional<Order> result = adapter.findById("ord-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().getAllReservationIds().isEmpty());
    }

    @Test
    void testSaveMapsVersionField() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", "resv-1", java.time.Instant.now(),
                java.util.Collections.emptyList(), 0L);

        adapter.save(order);

        verify(repository).save(argThat(entity ->
                entity.getVersion() != null && entity.getVersion() == 0L));
    }
}
