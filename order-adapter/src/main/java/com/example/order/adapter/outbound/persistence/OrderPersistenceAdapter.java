package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.out.OrderRepositoryPort;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceAdapter.class);
    private final ObjectMapper objectMapper;

    private final OrderJpaRepository repository;

    public OrderPersistenceAdapter(OrderJpaRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.addMixIn(OrderItem.class, OrderItemMixin.class);
    }

    @Override
    public void save(Order order) {
        repository.save(toEntity(order));
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return repository.findById(orderId).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public void updateStatus(String orderId, OrderStatus status) {
        repository.updateStatus(orderId, status.name());
    }

    private OrderEntity toEntity(Order order) {
        String itemsJson = serializeItems(order.getItems());
        String reservationIdsJson = serializeReservationIds(order.getAllReservationIds());
        OrderEntity entity = new OrderEntity(
                order.getOrderId(),
                order.getCustomerId(),
                order.getIdempotencyKey(),
                order.getReservationId(),
                order.getStatus().name(),
                LocalDateTime.ofInstant(order.getCreatedAt(), java.time.ZoneOffset.UTC));
        entity.setItems(itemsJson);
        entity.setReservationIds(reservationIdsJson);
        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = deserializeItems(entity.getItems());
        List<String> allReservationIds = deserializeReservationIds(entity.getReservationIds());
        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                items,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getIdempotencyKey(),
                entity.getReservationId(),
                entity.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
                allReservationIds);
    }

    private String serializeItems(List<OrderItem> items) {
        try {
            if (items == null || items.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize order items", e);
        }
    }

    private List<OrderItem> deserializeItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<List<OrderItem>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize order items from JSON — data integrity issue for itemsJson='{}'", itemsJson, e);
            throw new IllegalStateException("Failed to deserialize order items — possible data corruption", e);
        }
    }

    private String serializeReservationIds(List<String> reservationIds) {
        try {
            if (reservationIds == null || reservationIds.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(reservationIds);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize reservation IDs", e);
        }
    }

    private List<String> deserializeReservationIds(String reservationIdsJson) {
        if (reservationIdsJson == null || reservationIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(reservationIdsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize reservation IDs from JSON: {}", reservationIdsJson, e);
            throw new IllegalStateException("Failed to deserialize reservation IDs", e);
        }
    }

    @JsonCreator
    private static OrderItem createOrderItem(@JsonProperty("sku") String sku, @JsonProperty("quantity") int quantity) {
        return new OrderItem(sku, quantity);
    }

    private abstract static class OrderItemMixin {
        @JsonCreator
        public OrderItemMixin(@JsonProperty("sku") String sku, @JsonProperty("quantity") int quantity) {}
    }
}
