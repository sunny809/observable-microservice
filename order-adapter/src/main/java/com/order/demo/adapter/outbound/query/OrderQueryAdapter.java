package com.order.demo.adapter.outbound.query;

import com.order.demo.application.port.in.OrderDetail;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.in.OrderSearchCriteria;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.in.SagaStepView;
import com.order.demo.application.port.out.OrderQueryPort;
import com.order.demo.application.port.out.OrderSnapshotPort;
import com.order.demo.application.port.out.SagaLogEntry;
import com.order.demo.application.port.out.SagaLogPort;
import com.order.demo.adapter.outbound.persistence.OrderEntity;
import com.order.demo.adapter.outbound.persistence.OrderJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class OrderQueryAdapter implements OrderQueryPort {

    private final OrderViewJpaRepository viewRepository;
    private final SagaLogPort sagaLogPort;
    private final OrderSnapshotPort orderSnapshotPort;
    private final OrderJpaRepository orderRepository;

    public OrderQueryAdapter(OrderViewJpaRepository viewRepository,
                             SagaLogPort sagaLogPort,
                             OrderSnapshotPort orderSnapshotPort,
                             OrderJpaRepository orderRepository) {
        this.viewRepository = viewRepository;
        this.sagaLogPort = sagaLogPort;
        this.orderSnapshotPort = orderSnapshotPort;
        this.orderRepository = orderRepository;
    }

    @Override
    public Page<OrderSummary> search(OrderSearchCriteria criteria) {
        Specification<OrderViewEntity> spec = Specification.where(null);

        if (criteria.customerId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("customerId"), criteria.customerId()));
        }
        if (criteria.status() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), criteria.status()));
        }
        if (criteria.createdAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"),
                            criteria.createdAfter().atZone(ZoneOffset.UTC).toLocalDateTime()));
        }
        if (criteria.createdBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"),
                            criteria.createdBefore().atZone(ZoneOffset.UTC).toLocalDateTime()));
        }

        return viewRepository.findAll(spec,
                PageRequest.of(criteria.page(), criteria.size()))
            .map(this::toSummary);
    }

    @Override
    public Optional<OrderDetail> findDetail(String orderId) {
        return viewRepository.findById(orderId)
                .map(view -> {
                    List<SagaStepView> steps = sagaLogPort.findByOrderId(orderId).stream()
                            .map(this::toStepView)
                            .toList();
                    return new OrderDetail(toSummary(view), steps);
                });
    }

    @Override
    public List<OrderSummary> findBySku(String sku) {
        // Escape LIKE wildcard chars and JSON special chars in the SKU value
        String escaped = sku.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String skuPattern = "%\"sku\":\"" + escaped + "\"%";
        return orderRepository.findByItemsContainingSku(skuPattern).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<OrderSummary> findOrderAt(String orderId, Instant pointInTime) {
        return orderSnapshotPort.findSnapshotAt(orderId, pointInTime);
    }

    private OrderSummary toSummary(OrderViewEntity e) {
        return new OrderSummary(
                e.getId(), e.getCustomerId(), e.getStatus(),
                e.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                e.getReservationCount() != null ? e.getReservationCount() : 0,
                e.getTotalQuantity() != null ? e.getTotalQuantity() : 0,
                e.getLastSagaStep(), e.getLastSagaStatus()
        );
    }

    private OrderSummary toSummary(OrderEntity e) {
        return new OrderSummary(
                e.getId(), e.getCustomerId(), e.getStatus(),
                e.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                e.getReservationIds() != null ? e.getReservationIds().size() : 0,
                e.getItems() != null ? e.getItems().stream().mapToInt(OrderItem::getQuantity).sum() : 0,
                null, null
        );
    }

    private SagaStepView toStepView(SagaLogEntry entry) {
        return new SagaStepView(
                entry.stepName(), entry.stepStatus(),
                entry.startedAt() != null ? entry.startedAt().atZone(ZoneOffset.UTC).toInstant() : null,
                entry.completedAt() != null ? entry.completedAt().atZone(ZoneOffset.UTC).toInstant() : null,
                entry.detail()
        );
    }
}
