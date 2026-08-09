package com.order.demo.adapter.outbound.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
                .map(entity -> {
                    SnapshotFields fields = deserializeSnapshot(entity.getSnapshot());
                    return new OrderSummary(
                            entity.getOrderId(),
                            fields.customerId(),
                            entity.getStatus(),
                            entity.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                            fields.reservationCount(),
                            fields.totalQuantity(),
                            null, entity.getReason()
                    );
                });
    }

    /**
     * Deserializes the stored Order JSON to extract the summary fields that are
     * not persisted as dedicated columns. The snapshot is the full Order serialized
     * via {@link #serializeOrder(Order)}, so customerId, items, and allReservationIds
     * are read back from the JSON tree.
     *
     * @param snapshot the stored Order JSON
     * @return the extracted summary fields
     */
    private static SnapshotFields deserializeSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return new SnapshotFields(null, 0, 0);
        }
        try {
            JsonNode root = MAPPER.readTree(snapshot);
            String customerId = root.hasNonNull("customerId") ? root.get("customerId").asText() : null;

            int totalQuantity = 0;
            if (root.hasNonNull("items") && root.get("items").isArray()) {
                for (JsonNode item : root.get("items")) {
                    if (item.hasNonNull("quantity")) {
                        totalQuantity += item.get("quantity").asInt();
                    }
                }
            }

            int reservationCount = 0;
            if (root.hasNonNull("allReservationIds") && root.get("allReservationIds").isArray()) {
                reservationCount = root.get("allReservationIds").size();
            }

            return new SnapshotFields(customerId, reservationCount, totalQuantity);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order snapshot", e);
            return new SnapshotFields(null, 0, 0);
        }
    }

    private record SnapshotFields(String customerId, int reservationCount, int totalQuantity) {}

    private String serializeOrder(Order order) {
        try {
            return MAPPER.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order snapshot for orderId={}", order.getOrderId(), e);
            return "{}";
        }
    }
}
