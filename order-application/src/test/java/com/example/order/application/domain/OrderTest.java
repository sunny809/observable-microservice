package com.example.order.application.domain;

import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.PlaceOrderCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private PlaceOrderCommand createCommand() {
        return new PlaceOrderCommand("cust-1", List.of(new OrderItem("SKU-1", 2)), "idem-key-1");
    }

    @Test
    void testCreateWithValidCommand() {
        PlaceOrderCommand command = createCommand();
        Order order = Order.create(command, "resv-123");

        assertNotNull(order.getOrderId());
        assertEquals("cust-1", order.getCustomerId());
        assertEquals(1, order.getItems().size());
        assertEquals("SKU-1", order.getItems().get(0).getSku());
        assertEquals(2, order.getItems().get(0).getQuantity());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals("idem-key-1", order.getIdempotencyKey());
        assertEquals("resv-123", order.getReservationId());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void testGettersReturnCorrectValues() {
        Instant now = Instant.now();
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-A", 3)),
                OrderStatus.RESERVED, "idem-1", "resv-1", now);

        assertEquals("ord-1", order.getOrderId());
        assertEquals("cust-1", order.getCustomerId());
        assertEquals(1, order.getItems().size());
        assertEquals(OrderStatus.RESERVED, order.getStatus());
        assertEquals("idem-1", order.getIdempotencyKey());
        assertEquals("resv-1", order.getReservationId());
        assertEquals(now, order.getCreatedAt());
    }

    @Test
    void testConstructorRejectsNullOrderId() {
        assertThrows(NullPointerException.class, () ->
                new Order(null, "cust-1", List.of(new OrderItem("SKU-1", 1)), OrderStatus.CREATED, "idem-1", "resv-1", Instant.now()));
    }

    @Test
    void testConstructorRejectsNullCustomerId() {
        assertThrows(NullPointerException.class, () ->
                new Order("ord-1", null, List.of(new OrderItem("SKU-1", 1)), OrderStatus.CREATED, "idem-1", "resv-1", Instant.now()));
    }

    @Test
    void testConstructorRejectsNullItems() {
        assertThrows(NullPointerException.class, () ->
                new Order("ord-1", "cust-1", null, OrderStatus.CREATED, "idem-1", "resv-1", Instant.now()));
    }

    @Test
    void testConstructorRejectsNullStatus() {
        assertThrows(NullPointerException.class, () ->
                new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)), null, "idem-1", "resv-1", Instant.now()));
    }

    @Test
    void testConstructorRejectsNullIdempotencyKey() {
        assertThrows(NullPointerException.class, () ->
                new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)), OrderStatus.CREATED, null, "resv-1", Instant.now()));
    }

    @Test
    void testConstructorAcceptsNullReservationId() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)),
                OrderStatus.CREATED, "idem-1", null, Instant.now());
        assertNull(order.getReservationId());
    }

    @Test
    void testConstructorRejectsNullCreatedAt() {
        assertThrows(NullPointerException.class, () ->
                new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)), OrderStatus.CREATED, "idem-1", "resv-1", null));
    }

    @Test
    void testSetStatusUpdatesStatus() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)),
                OrderStatus.CREATED, "idem-1", "resv-1", Instant.now());
        order.setStatus(OrderStatus.WMS_ACKED);
        assertEquals(OrderStatus.WMS_ACKED, order.getStatus());
    }

    @Test
    void testSetReservationIdUpdatesId() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 1)),
                OrderStatus.CREATED, "idem-1", null, Instant.now());
        order.setReservationId("new-resv");
        assertEquals("new-resv", order.getReservationId());
    }
}
