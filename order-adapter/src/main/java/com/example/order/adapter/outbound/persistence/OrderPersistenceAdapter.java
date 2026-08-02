package com.order.demo.adapter.outbound.persistence;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.out.OrderRepositoryPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceAdapter.class);

    private final OrderJpaRepository repository;

    public OrderPersistenceAdapter(OrderJpaRepository repository) {
        this.repository = repository;
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

    @Override
    public boolean updateStatusWithVersion(String orderId, OrderStatus newStatus,
                                           OrderStatus expectedStatus, long expectedVersion) {
        int affected = repository.updateStatusWithVersion(
                orderId, newStatus.name(), expectedStatus.name(), expectedVersion);
        return affected > 0;
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity(
                order.getOrderId(),
                order.getCustomerId(),
                order.getIdempotencyKey(),
                order.getReservationId(),
                order.getStatus().name(),
                LocalDateTime.ofInstant(order.getCreatedAt(), java.time.ZoneOffset.UTC));
        entity.setItems(order.getItems());
        entity.setReservationIds(order.getAllReservationIds());
        if (order.getVersion() != null) {
            entity.setVersion(order.getVersion());
        }
        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        return new Order(
                entity.getId(),
                entity.getCustomerId(),
                entity.getItems() != null ? entity.getItems() : List.of(),
                OrderStatus.valueOf(entity.getStatus()),
                entity.getIdempotencyKey(),
                entity.getReservationId(),
                entity.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
                entity.getReservationIds() != null ? entity.getReservationIds() : List.of(),
                entity.getVersion());
    }
}
