package com.example.order.adapter.outbound.persistence;

import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.out.OrderRepositoryPort;
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
        OrderEntity entity = new OrderEntity(
                order.getOrderId(),
                order.getCustomerId(),
                order.getIdempotencyKey(),
                order.getReservationId(),
                order.getStatus().name(),
                LocalDateTime.ofInstant(order.getCreatedAt(), java.time.ZoneOffset.UTC));
        entity.setItems(itemsJson);
        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = deserializeItems(entity.getItems());
        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                items,
                OrderStatus.valueOf(entity.getStatus()),
                entity.getIdempotencyKey(),
                entity.getReservationId(),
                entity.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant());
    }

    private String serializeItems(List<OrderItem> items) {
        try {
            if (items == null || items.isEmpty()) {
                return "[]";
            }
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("Failed to serialize order items, returning empty JSON array", e);
            return "[]";
        }
    }

    private List<OrderItem> deserializeItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(itemsJson, new TypeReference<List<OrderItem>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize order items from JSON, returning empty list", e);
            return Collections.emptyList();
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
