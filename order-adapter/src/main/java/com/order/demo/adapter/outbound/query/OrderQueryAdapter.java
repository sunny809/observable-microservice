package com.order.demo.adapter.outbound.query;

import com.order.demo.application.port.in.OrderDetail;
import com.order.demo.application.port.in.OrderSearchCriteria;
import com.order.demo.application.port.in.OrderSummary;
import com.order.demo.application.port.in.SagaStepView;
import com.order.demo.application.port.out.OrderQueryPort;
import com.order.demo.application.port.out.OrderSnapshotPort;
import com.order.demo.application.port.out.SagaLogEntry;
import com.order.demo.application.port.out.SagaLogPort;
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

    public OrderQueryAdapter(OrderViewJpaRepository viewRepository,
                             SagaLogPort sagaLogPort,
                             OrderSnapshotPort orderSnapshotPort) {
        this.viewRepository = viewRepository;
        this.sagaLogPort = sagaLogPort;
        this.orderSnapshotPort = orderSnapshotPort;
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

    private SagaStepView toStepView(SagaLogEntry entry) {
        return new SagaStepView(
                entry.stepName(), entry.stepStatus(),
                entry.startedAt() != null ? entry.startedAt().atZone(ZoneOffset.UTC).toInstant() : null,
                entry.completedAt() != null ? entry.completedAt().atZone(ZoneOffset.UTC).toInstant() : null,
                entry.detail()
        );
    }
}
