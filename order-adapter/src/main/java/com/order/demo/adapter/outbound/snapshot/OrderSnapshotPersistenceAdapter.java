package com.order.demo.adapter.outbound.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.demo.application.domain.Order;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.out.OrderSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
public class OrderSnapshotPersistenceAdapter implements OrderSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(OrderSnapshotPersistenceAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrderSnapshotJpaRepository repository;

    public OrderSnapshotPersistenceAdapter(OrderSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveSnapshot(Order order, String reason) {
        OrderSnapshotEntity entity = new OrderSnapshotEntity();
        entity.setOrderId(order.getOrderId());
        entity.setStatus(order.getStatus().name());
        entity.setSnapshot(serializeOrder(order));
        entity.setVersion(order.getVersion() != null ? order.getVersion() : 0L);
        entity.setReason(reason);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public Optional<OrderSummary> findSnapshotAt(String orderId, java.time.Instant pointInTime) {
        LocalDateTime ldt = pointInTime.atZone(ZoneOffset.UTC).toLocalDateTime();
        return repository.findLatestSnapshotAt(orderId, ldt)
                .map(entity -> new OrderSummary(
                        entity.getOrderId(),
                        null, // customerId not in snapshot
                        entity.getStatus(),
                        entity.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                        0, 0, // reservation/quantity not in snapshot
                        null, entity.getReason()
                ));
    }

    private String serializeOrder(Order order) {
        try {
            return MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order snapshot for orderId={}", order.getOrderId(), e);
            return "{}";
        }
    }
}
