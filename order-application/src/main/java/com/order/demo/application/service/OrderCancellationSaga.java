package com.order.demo.application.service;

import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderCancelledEvent;
import com.order.demo.application.domain.OrderNotFoundException;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.port.in.CancelOrderCommand;
import com.order.demo.application.port.in.CancelOrderUseCase;
import com.order.demo.application.port.in.OrderCancelledResult;
import com.order.demo.application.port.out.CompensationLogPort;
import com.order.demo.application.port.out.CompensationStatus;
import com.order.demo.application.port.out.DomainEventPublisher;
import com.order.demo.application.port.out.InventoryPort;
import com.order.demo.application.port.out.MetricsPort;
import com.order.demo.application.port.out.OrderRepositoryPort;
import com.order.demo.application.port.out.OrderSnapshotPort;
import com.order.demo.application.port.out.SagaLogPort;
import com.order.demo.application.port.out.WmsPort;
import com.order.demo.application.port.out.WmsShipmentInstruction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the cancellation of a previously placed order using the Saga
 * pattern's compensating-transaction logic.
 *
 * <p>Cancellation is the reverse of {@link OrderPlacementSaga}: it releases the
 * reserved inventory, voids the WMS shipment instruction (if one was sent), and
 * transitions the order to {@link OrderStatus#CANCELLED}. Unlike placement,
 * cancellation is fully synchronous — it completes within a single transaction
 * before returning to the caller.
 *
 * <p>An order may be cancelled from {@code CREATED}, {@code RESERVED},
 * {@code WMS_ACKED}, or {@code WMS_PICKED}. Once dispatched
 * ({@code TMS_DISPATCHED}) or otherwise terminal ({@code REJECTED},
 * {@code TMS_REJECTED}), cancellation is refused.
 */
@Service
public class OrderCancellationSaga implements CancelOrderUseCase {

    private static final String SAGA_STEP_CANCELLED = "CANCELLED";

    private final OrderRepositoryPort orderRepository;
    private final InventoryPort inventoryPort;
    private final WmsPort wmsPort;
    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;
    private final CompensationLogPort compensationLogPort;
    private final OrderSnapshotPort orderSnapshotPort;
    private final MetricsPort metricsPort;

    public OrderCancellationSaga(OrderRepositoryPort orderRepository,
                                 InventoryPort inventoryPort,
                                 WmsPort wmsPort,
                                 SagaLogPort sagaLogPort,
                                 DomainEventPublisher eventPublisher,
                                 CompensationLogPort compensationLogPort,
                                 OrderSnapshotPort orderSnapshotPort,
                                 MetricsPort metricsPort) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.wmsPort = wmsPort;
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
        this.compensationLogPort = compensationLogPort;
        this.orderSnapshotPort = orderSnapshotPort;
        this.metricsPort = metricsPort;
    }

    @Override
    @Transactional
    public OrderCancelledResult cancel(CancelOrderCommand command) {
        long start = System.nanoTime();

        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        if (!command.customerId().equals(order.getCustomerId())) {
            throw new IllegalStateException(
                    "Order " + order.getOrderId() + " does not belong to customer " + command.customerId());
        }

        // Idempotency: a second cancel of an already-cancelled order is a no-op.
        if (order.getStatus() == OrderStatus.CANCELLED) {
            metricsPort.recordSagaDuration(elapsedMillis(start), "cancelled");
            return new OrderCancelledResult(order.getOrderId(), OrderStatus.CANCELLED, "already cancelled");
        }

        // Validate the transition. Throws IllegalStateException if the order is
        // TMS_DISPATCHED / REJECTED / TMS_REJECTED (not cancellable).
        order.transitionTo(OrderStatus.CANCELLED);

        // Compensation: release reserved inventory, then void WMS if instructed.
        releaseReservations(order);
        if (order.getStatus() == OrderStatus.WMS_ACKED || order.getStatus() == OrderStatus.WMS_PICKED) {
            voidWmsInstruction(order);
        }

        orderRepository.updateStatus(order.getOrderId(), OrderStatus.CANCELLED);
        orderSnapshotPort.saveSnapshot(order.transitionTo(OrderStatus.CANCELLED), SAGA_STEP_CANCELLED);

        sagaLogPort.recordSagaStepStarted(order.getOrderId(), SAGA_STEP_CANCELLED);
        sagaLogPort.recordSagaStepCompleted(order.getOrderId(), SAGA_STEP_CANCELLED,
                "Order cancelled: " + command.reason());
        metricsPort.recordSagaDuration(elapsedMillis(start), "cancelled");
        eventPublisher.publish(new OrderCancelledEvent(order.getOrderId(), command.reason()));

        return new OrderCancelledResult(order.getOrderId(), OrderStatus.CANCELLED, "cancelled");
    }

    /**
     * Releases every reserved inventory item, with per-reservation idempotency so a
     * retried cancellation does not double-release. A release failure is logged and
     * does not abort the remaining releases — cancellation still proceeds.
     */
    private void releaseReservations(Order order) {
        for (String reservationId : order.getAllReservationIds()) {
            String idempotencyKey = compensationIdempotencyKey(order.getOrderId(), reservationId);
            if (compensationLogPort.exists(idempotencyKey)) {
                sagaLogPort.recordCompensation(order.getOrderId(), reservationId, "Skip: already compensated");
                continue;
            }
            try {
                inventoryPort.release(reservationId).join();
            } catch (Exception e) {
                sagaLogPort.recordCompensation(order.getOrderId(), reservationId,
                        "Release failed during cancellation: " + e.getMessage());
                saveCompensationLog(idempotencyKey, order.getOrderId(), reservationId,
                        CompensationStatus.FAILED, e.getMessage());
                continue;
            }
            saveCompensationLog(idempotencyKey, order.getOrderId(), reservationId,
                    CompensationStatus.COMPLETED, null);
        }
    }

    /**
     * Voids the WMS shipment instruction. Best-effort: a WMS void failure is logged
     * but does not block cancellation — the order is still marked CANCELLED and the
     * inventory release remains the authoritative compensation.
     */
    private void voidWmsInstruction(Order order) {
        try {
            wmsPort.cancelInstruction(new WmsShipmentInstruction(order.getOrderId(), order.getReservationId())).join();
            sagaLogPort.recordCompensation(order.getOrderId(), order.getReservationId(), "WMS cancel instruction sent");
        } catch (Exception e) {
            sagaLogPort.recordCompensation(order.getOrderId(), order.getReservationId(),
                    "WMS cancel failed (best-effort): " + e.getMessage());
        }
    }

    private void saveCompensationLog(String idempotencyKey, String orderId, String reservationId,
                                     CompensationStatus status, String errorMessage) {
        try {
            compensationLogPort.save(idempotencyKey, orderId, SAGA_STEP_CANCELLED, reservationId, status, errorMessage);
        } catch (Exception e) {
            sagaLogPort.recordCompensation(orderId, reservationId, "Failed to save compensation log: " + e.getMessage());
        }
    }

    private String compensationIdempotencyKey(String orderId, String reservationId) {
        return String.format("compensate:%s:%s:%s", orderId, SAGA_STEP_CANCELLED, reservationId);
    }

    private static long elapsedMillis(long start) {
        return java.time.Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
