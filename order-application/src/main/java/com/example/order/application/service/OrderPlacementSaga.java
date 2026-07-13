package com.example.order.application.service;

import com.example.order.application.domain.DuplicateOrderException;
import com.example.order.application.domain.InsufficientInventoryException;
import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.TmsInstructionRequiredEvent;
import com.example.order.application.domain.WmsInstructionRequiredEvent;
import com.example.order.application.domain.WmsPickingCompletedEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.OrderPlacedResult;
import com.example.order.application.port.in.PlaceOrderCommand;
import com.example.order.application.port.in.PlaceOrderUseCase;
import com.example.order.application.port.out.CompensationLogPort;
import com.example.order.application.port.out.CompensationStatus;
import com.example.order.application.port.out.ConfirmReservationCommand;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.IdempotencyCachePort;
import com.example.order.application.port.out.InventoryConfirmationScheduler;
import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.MetricsPort;
import com.example.order.application.port.out.OrderRepositoryPort;
import com.example.order.application.port.out.ReservationRequest;
import com.example.order.application.port.out.SagaLogPort;
import com.example.order.application.port.out.TmsAck;
import com.example.order.application.port.out.TmsPort;
import com.example.order.application.port.out.TmsShipmentInstruction;
import com.example.order.application.port.out.WmsAck;
import com.example.order.application.port.out.WmsPort;
import com.example.order.application.port.out.WmsShipmentInstruction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates the order placement saga using the Saga pattern.
 *
 * <p>The saga coordinates multiple distributed operations (inventory reservation,
 * order persistence, WMS instruction, WMS picking, TMS dispatch) with compensating
 * transactions on failure. The {@code placeOrder} method runs within a single
 * Spring transaction. After the transaction commits, async event listeners fire
 * to handle WMS and TMS operations.
 *
 * <p>Order status lifecycle:
 * <pre>
 * CREATED → WMS_ACKED → WMS_PICKED → TMS_DISPATCHED
 *    ↓         ↓            ↓              ↓
 * REJECTED  REJECTED   TMS_REJECTED   (terminal)
 * </pre>
 *
 * <p>Because async callbacks run on Reactor threads without an active Spring
 * transaction, all database operations in callbacks are wrapped with
 * {@link TransactionTemplate}.
 */
@Service
public class OrderPlacementSaga implements PlaceOrderUseCase {

    private static final String SAGA_STEP_ORDER_CREATED = "ORDER_CREATED";
    private static final String SAGA_STEP_WMS_ACKED = "WMS_ACKED";
    private static final String SAGA_STEP_WMS_PICKED = "WMS_PICKED";
    private static final String SAGA_STEP_TMS_DISPATCHED = "TMS_DISPATCHED";
    private static final String SAGA_STEP_TMS_REJECTED = "TMS_REJECTED";

    private final OrderRepositoryPort orderRepository;
    private final InventoryPort inventoryPort;
    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;
    private final WmsPort wmsPort;
    private final TmsPort tmsPort;
    private final InventoryConfirmationScheduler confirmationScheduler;
    private final TransactionTemplate transactionTemplate;
    private final IdempotencyCachePort idempotencyCache;
    private final MetricsPort metricsPort;
    private final CompensationLogPort compensationLogPort;

    public OrderPlacementSaga(OrderRepositoryPort orderRepository,
                              InventoryPort inventoryPort,
                              SagaLogPort sagaLogPort,
                              DomainEventPublisher eventPublisher,
                              WmsPort wmsPort,
                              TmsPort tmsPort,
                              InventoryConfirmationScheduler confirmationScheduler,
                              TransactionTemplate transactionTemplate,
                              IdempotencyCachePort idempotencyCache,
                              MetricsPort metricsPort,
                              CompensationLogPort compensationLogPort) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
        this.wmsPort = wmsPort;
        this.tmsPort = tmsPort;
        this.confirmationScheduler = confirmationScheduler;
        this.transactionTemplate = transactionTemplate;
        this.idempotencyCache = idempotencyCache;
        this.metricsPort = metricsPort;
        this.compensationLogPort = compensationLogPort;
    }

    /**
     * Places a new order by validating idempotency, reserving inventory,
     * persisting the order, and publishing a WMS instruction event.
     *
     * <p>Compensation: if any inventory reservation fails, all previously
     * reserved items are released and {@link InsufficientInventoryException}
     * is thrown. The order is not persisted.
     *
     * @param command the place order command containing customer, items, and idempotency key
     * @return the result containing the generated order ID and initial status
     * @throws DuplicateOrderException if an order with the same idempotency key already exists
     * @throws InsufficientInventoryException if any item cannot be reserved
     */
    @Override
    @Transactional
    public OrderPlacedResult placeOrder(PlaceOrderCommand command) {
        long start = System.nanoTime();
        // Fast path: cache hit means we already processed this key — throw immediately
        if (idempotencyCache.exists(command.getIdempotencyKey())) {
            metricsPort.recordOrderFailed("DUPLICATE_ORDER");
            metricsPort.recordSagaDuration(elapsedMillis(start), "duplicate");
            throw new DuplicateOrderException(command.getIdempotencyKey());
        }
        // Safety net: check DB in case cache expired or was evicted
        orderRepository.findByIdempotencyKey(command.getIdempotencyKey()).ifPresent(order -> {
            metricsPort.recordOrderFailed("DUPLICATE_ORDER");
            metricsPort.recordSagaDuration(elapsedMillis(start), "duplicate");
            throw new DuplicateOrderException(command.getIdempotencyKey());
        });

        String orderId = UUID.randomUUID().toString();
        List<InventoryReservation> reservations;
        try {
            reservations = reserveAllItems(command, orderId);
        } catch (InsufficientInventoryException e) {
            metricsPort.recordOrderFailed("INSUFFICIENT_INVENTORY");
            metricsPort.recordSagaDuration(elapsedMillis(start), "compensation");
            throw e;
        }
        InventoryReservation primaryReservation = reservations.get(0);

        List<String> allReservationIds = reservations.stream()
                .map(InventoryReservation::getReservationId)
                .toList();

        Order order = new Order(orderId,
                command.getCustomerId(),
                command.getItems(),
                OrderStatus.CREATED,
                command.getIdempotencyKey(),
                primaryReservation.getReservationId(),
                Instant.now(),
                allReservationIds);
        orderRepository.save(order);

        // Cache the idempotency key after successful save
        idempotencyCache.put(command.getIdempotencyKey());

        String logMsg = String.format("Order persisted with %d reservation(s): %s",
                    reservations.size(), reservations.stream().map(InventoryReservation::getReservationId).toList());
            sagaLogPort.recordStep(order.getOrderId(), SAGA_STEP_ORDER_CREATED, logMsg);
        eventPublisher.publish(new WmsInstructionRequiredEvent(order.getOrderId(),
                new WmsShipmentInstruction(order.getOrderId(), primaryReservation.getReservationId()),
                reservations));

        metricsPort.recordOrderPlaced(order.getStatus().name());
        metricsPort.recordSagaDuration(elapsedMillis(start), "success");
        return new OrderPlacedResult(order.getOrderId(), order.getStatus(), null);
    }

    /**
     * Handles the WMS instruction asynchronously after the order transaction
     * has committed. Uses {@link TransactionTemplate} to execute database
     * operations because the callback runs on a Reactor thread outside any
     * Spring transaction context.
     *
     * <p>If WMS accepts the instruction, the inventory is confirmed and the
     * order status is updated to {@code WMS_ACKED}. If WMS rejects or the
     * call fails, all reservations are released and the order status is set
     * to {@code REJECTED}.
     *
     * @param event the event containing order and reservation information
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWmsRequired(WmsInstructionRequiredEvent event) {
        InventoryReservation primaryReservation = event.getReservations().get(0);
        List<InventoryReservation> allReservations = event.getReservations();
        // Idle gap between order transaction commit and this listener firing
        metricsPort.recordSagaGap("POST_COMMIT_TO_WMS",
                java.time.Duration.between(event.getEmittedAt(), Instant.now()).toMillis());
        confirmationScheduler.scheduleConfirmation(primaryReservation);

        long stepStart = System.nanoTime();
        wmsPort.sendInstruction(event.getInstruction())
                .thenCompose(ack -> {
                    if (ack.isAccepted()) {
                        return inventoryPort.confirm(new ConfirmReservationCommand(primaryReservation.getReservationId()))
                                .thenRun(() -> transactionTemplate.executeWithoutResult(status -> {
                                    orderRepository.updateStatus(event.getOrderId(), OrderStatus.WMS_ACKED);
                                    metricsPort.recordSagaStepDuration(SAGA_STEP_WMS_ACKED, elapsedMillis(stepStart), "success");
                                    sagaLogPort.recordStep(event.getOrderId(), SAGA_STEP_WMS_ACKED, String.format("WMS accepted instruction %s", ack.getMessageId()));
                                }));
                    } else {
                        return releaseAllAsync(allReservations, SAGA_STEP_WMS_ACKED)
                                .thenRun(() -> transactionTemplate.executeWithoutResult(status -> {
                                    orderRepository.updateStatus(event.getOrderId(), OrderStatus.REJECTED);
                                    metricsPort.recordSagaStepDuration(SAGA_STEP_WMS_ACKED, elapsedMillis(stepStart), "rejected");
                                    sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(), String.format("WMS rejected: %s", ack.getMessageId()));
                                }));
                    }
                })
                .exceptionally(ex -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        releaseAll(allReservations, SAGA_STEP_WMS_ACKED);
                        orderRepository.updateStatus(event.getOrderId(), OrderStatus.REJECTED);
                        metricsPort.recordSagaStepDuration(SAGA_STEP_WMS_ACKED, elapsedMillis(stepStart), "failure");
                        sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(), String.format("WMS transport failed: %s", ex.getMessage()));
                    });
                    return null;
                });
    }

    /**
     * Handles WMS picking completion asynchronously after the WMS confirms
     * that order picking is complete. Updates the order status to
     * {@code WMS_PICKED} and publishes a {@link TmsInstructionRequiredEvent}
     * to trigger the TMS dispatch phase.
     *
     * @param event the event containing order and reservation information
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWmsPickingCompleted(WmsPickingCompletedEvent event) {
        InventoryReservation primaryReservation = event.getReservations().get(0);
        List<InventoryReservation> allReservations = event.getReservations();

        long stepStart = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.updateStatus(event.getOrderId(), OrderStatus.WMS_PICKED);
            metricsPort.recordSagaStepDuration(SAGA_STEP_WMS_PICKED, elapsedMillis(stepStart), "success");
            sagaLogPort.recordStep(event.getOrderId(), SAGA_STEP_WMS_PICKED,
                    String.format("WMS confirmed picking complete for reservation %s", primaryReservation.getReservationId()));
        });

        // Publish inside a transaction so that @TransactionalEventListener(AFTER_COMMIT)
        // on onTmsRequired fires after the transaction commits.
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(new TmsInstructionRequiredEvent(event.getOrderId(),
                    new TmsShipmentInstruction(event.getOrderId(), primaryReservation.getReservationId()),
                    allReservations));
        });
    }

    /**
     * Handles the TMS instruction asynchronously after the WMS picking
     * is complete. Uses {@link TransactionTemplate} to execute database
     * operations because the callback runs on a Reactor thread outside any
     * Spring transaction context.
     *
     * <p>If TMS accepts the instruction, the order status is updated to
     * {@code TMS_DISPATCHED}. If TMS rejects or the call fails, all
     * reservations are released and the order status is set to
     * {@code TMS_REJECTED}.
     *
     * @param event the event containing order and reservation information
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTmsRequired(TmsInstructionRequiredEvent event) {
        InventoryReservation primaryReservation = event.getReservations().get(0);
        List<InventoryReservation> allReservations = event.getReservations();

        long stepStart = System.nanoTime();
        tmsPort.sendInstruction(event.getInstruction())
                .thenCompose(ack -> {
                    if (ack.isAccepted()) {
                        return CompletableFuture.completedFuture(null)
                                .thenRun(() -> transactionTemplate.executeWithoutResult(status -> {
                            orderRepository.updateStatus(event.getOrderId(), OrderStatus.TMS_DISPATCHED);
                            metricsPort.recordSagaStepDuration(SAGA_STEP_TMS_DISPATCHED, elapsedMillis(stepStart), "success");
                            sagaLogPort.recordStep(event.getOrderId(), SAGA_STEP_TMS_DISPATCHED,
                                    String.format("TMS accepted dispatch instruction %s", ack.getMessageId()));
                        }));
                    } else {
                        return releaseAllAsync(allReservations, SAGA_STEP_TMS_DISPATCHED)
                                .thenRun(() -> transactionTemplate.executeWithoutResult(status -> {
                                    orderRepository.updateStatus(event.getOrderId(), OrderStatus.TMS_REJECTED);
                                    metricsPort.recordSagaStepDuration(SAGA_STEP_TMS_DISPATCHED, elapsedMillis(stepStart), "rejected");
                                    sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(),
                                            String.format("TMS rejected: %s", ack.getMessageId()));
                                }));
                    }
                })
                .exceptionally(ex -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        releaseAll(allReservations, SAGA_STEP_TMS_DISPATCHED);
                        orderRepository.updateStatus(event.getOrderId(), OrderStatus.TMS_REJECTED);
                        metricsPort.recordSagaStepDuration(SAGA_STEP_TMS_DISPATCHED, elapsedMillis(stepStart), "failure");
                        sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(),
                                String.format("TMS transport failed: %s", ex.getMessage()));
                    });
                    return null;
                });
    }

    /**
     * Reserves inventory for all items in the order synchronously. If any reservation fails,
     * all previously reserved items are released (compensation).
     *
     * @param command the place order command
     * @param orderId the generated order ID for correlation
     * @return list of successful reservations
     * @throws InsufficientInventoryException if any item cannot be reserved
     */
    private List<InventoryReservation> reserveAllItems(PlaceOrderCommand command, String orderId) {
        List<InventoryReservation> reservations = new ArrayList<>();
        for (OrderItem item : command.getItems()) {
            InventoryReservation reservation = inventoryPort.occupy(
                    new ReservationRequest(item.getSku(), item.getQuantity(), orderId)).join();
            if (reservation == null || reservation.getReservationId() == null) {
                metricsPort.recordInventoryReservation(item.getSku(), false);
                releaseAll(reservations, SAGA_STEP_ORDER_CREATED);
                throw new InsufficientInventoryException(item.getSku());
            }
            metricsPort.recordInventoryReservation(item.getSku(), true);
            reservations.add(reservation);
        }
        return reservations;
    }

    /**
     * Releases all inventory reservations. Exceptions during release are
     * logged as compensation failures but not propagated, to ensure the saga
     * can continue attempting to release remaining reservations.
     *
     * @param reservations the list of reservations to release
     */
    private void releaseAll(List<InventoryReservation> reservations, String stepName) {
        for (InventoryReservation r : reservations) {
            String idempotencyKey = compensationIdempotencyKey(r.getOrderId(), stepName, r.getReservationId());

            if (compensationLogPort.exists(idempotencyKey)) {
                sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                    "Skip: already compensated");
                continue;
            }

            try {
                inventoryPort.release(r.getReservationId()).join();
                compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                    r.getReservationId(), CompensationStatus.COMPLETED, null);
            } catch (Exception e) {
                compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                    r.getReservationId(), CompensationStatus.FAILED, e.getMessage());
                sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                    "Release failed during compensation: " + e.getMessage());
            }
        }
    }

    private CompletableFuture<Void> releaseAllAsync(List<InventoryReservation> reservations, String stepName) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (InventoryReservation r : reservations) {
            String idempotencyKey = compensationIdempotencyKey(r.getOrderId(), stepName, r.getReservationId());

            if (compensationLogPort.exists(idempotencyKey)) {
                sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                    "Skip: already compensated");
                continue;
            }

            futures.add(inventoryPort.release(r.getReservationId())
                .thenRun(() -> {
                    compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                        r.getReservationId(), CompensationStatus.COMPLETED, null);
                })
                .exceptionally(ex -> {
                    compensationLogPort.save(idempotencyKey, r.getOrderId(), stepName,
                        r.getReservationId(), CompensationStatus.FAILED, ex.getMessage());
                    sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                        "Release failed during compensation: " + ex.getMessage());
                    return null;
                }));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Converts a nanoTime delta to milliseconds.
     *
     * @param start the {@link System#nanoTime()} captured at the start of the timed span
     * @return elapsed milliseconds since {@code start}
     */
    private static long elapsedMillis(long start) {
        return java.time.Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private String compensationIdempotencyKey(String orderId, String stepName, String reservationId) {
        return String.format("compensate:%s:%s:%s", orderId, stepName, reservationId);
    }
}
