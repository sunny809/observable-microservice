package com.example.order.application.service;

import com.example.order.application.domain.DuplicateOrderException;
import com.example.order.application.domain.InsufficientInventoryException;
import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.WmsInstructionRequiredEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.in.OrderPlacedResult;
import com.example.order.application.port.in.PlaceOrderCommand;
import com.example.order.application.port.in.PlaceOrderUseCase;
import com.example.order.application.port.out.IdempotencyCachePort;
import com.example.order.application.port.out.ConfirmReservationCommand;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.InventoryConfirmationScheduler;
import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.OrderRepositoryPort;
import com.example.order.application.port.out.ReservationRequest;
import com.example.order.application.port.out.SagaLogPort;
import com.example.order.application.port.out.WmsPort;
import com.example.order.application.port.out.WmsShipmentInstruction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates the order placement saga using the Saga pattern.
 *
 * <p>The saga coordinates multiple distributed operations (inventory reservation,
 * order persistence, WMS instruction) with compensating transactions on failure.
 * The {@code placeOrder} method runs within a single Spring transaction. After
 * the transaction commits, the {@link #onWmsRequired(WmsInstructionRequiredEvent)}
 * listener fires asynchronously to send the WMS instruction. Because the async
 * callback runs on a Reactor thread without an active transaction, all database
 * operations in the callback are wrapped with {@link TransactionTemplate}.
 */
@Service
public class OrderPlacementSaga implements PlaceOrderUseCase {

    private static final String SAGA_STEP_ORDER_CREATED = "ORDER_CREATED";
    private static final String SAGA_STEP_WMS_ACKED = "WMS_ACKED";

    private final OrderRepositoryPort orderRepository;
    private final InventoryPort inventoryPort;
    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;
    private final WmsPort wmsPort;
    private final InventoryConfirmationScheduler confirmationScheduler;
    private final TransactionTemplate transactionTemplate;
    private final IdempotencyCachePort idempotencyCache;

    public OrderPlacementSaga(OrderRepositoryPort orderRepository,
                              InventoryPort inventoryPort,
                              SagaLogPort sagaLogPort,
                              DomainEventPublisher eventPublisher,
                              WmsPort wmsPort,
                              InventoryConfirmationScheduler confirmationScheduler,
                              TransactionTemplate transactionTemplate,
                              IdempotencyCachePort idempotencyCache) {
        this.orderRepository = orderRepository;
        this.inventoryPort = inventoryPort;
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
        this.wmsPort = wmsPort;
        this.confirmationScheduler = confirmationScheduler;
        this.transactionTemplate = transactionTemplate;
        this.idempotencyCache = idempotencyCache;
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
        // Fast path: check cache first to avoid DB hit for common duplicate case
        if (idempotencyCache.exists(command.getIdempotencyKey())) {
            orderRepository.findByIdempotencyKey(command.getIdempotencyKey()).ifPresent(order -> {
                throw new DuplicateOrderException(command.getIdempotencyKey());
            });
        } else {
            // Normal path: check DB for duplicates
            orderRepository.findByIdempotencyKey(command.getIdempotencyKey()).ifPresent(order -> {
                throw new DuplicateOrderException(command.getIdempotencyKey());
            });
        }

        String orderId = UUID.randomUUID().toString();
        List<InventoryReservation> reservations = reserveAllItems(command, orderId);
        InventoryReservation primaryReservation = reservations.get(0);

        Order order = new Order(orderId,
                command.getCustomerId(),
                command.getItems(),
                OrderStatus.CREATED,
                command.getIdempotencyKey(),
                primaryReservation.getReservationId(),
                Instant.now());
        orderRepository.save(order);

        // Cache the idempotency key after successful save
        idempotencyCache.put(command.getIdempotencyKey());

        String logMsg = String.format("Order persisted with %d reservation(s): %s",
                    reservations.size(), reservations.stream().map(InventoryReservation::getReservationId).toList());
            sagaLogPort.recordStep(order.getOrderId(), SAGA_STEP_ORDER_CREATED, logMsg);
        eventPublisher.publish(new WmsInstructionRequiredEvent(order.getOrderId(),
                new WmsShipmentInstruction(order.getOrderId(), primaryReservation.getReservationId()),
                reservations));

        return new OrderPlacedResult(order.getOrderId(), order.getStatus(), null);
    }

    /**
     * Reserves inventory for all items in the order. If any reservation fails,
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
                    new ReservationRequest(item.getSku(), item.getQuantity(), orderId));
            if (reservation == null || reservation.getReservationId() == null) {
                releaseAll(reservations);
                throw new InsufficientInventoryException(item.getSku());
            }
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
    private void releaseAll(List<InventoryReservation> reservations) {
        for (InventoryReservation r : reservations) {
            try {
                inventoryPort.release(r.getReservationId());
            } catch (Exception e) {
                sagaLogPort.recordCompensation(r.getOrderId(), r.getReservationId(),
                        "Release failed during compensation: " + e.getMessage());
            }
        }
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
        InventoryReservation primaryReservation = event.getReservation();
        List<InventoryReservation> allReservations = event.getReservations();
        confirmationScheduler.scheduleConfirmation(primaryReservation);

        wmsPort.sendInstruction(event.getInstruction())
                .thenAccept(ack -> transactionTemplate.executeWithoutResult(status -> {
                    if (ack.isAccepted()) {
                        inventoryPort.confirm(new ConfirmReservationCommand(primaryReservation.getReservationId()));
                        orderRepository.updateStatus(event.getOrderId(), OrderStatus.WMS_ACKED);
                        sagaLogPort.recordStep(event.getOrderId(), SAGA_STEP_WMS_ACKED, String.format("WMS accepted instruction %s", ack.getMessageId()));
                    } else {
                        releaseAll(allReservations);
                        orderRepository.updateStatus(event.getOrderId(), OrderStatus.REJECTED);
                        sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(), String.format("WMS rejected: %s", ack.getMessageId()));
                    }
                }))
                .exceptionally(ex -> {
                    transactionTemplate.executeWithoutResult(status -> {
                        releaseAll(allReservations);
                        orderRepository.updateStatus(event.getOrderId(), OrderStatus.REJECTED);
                        sagaLogPort.recordCompensation(event.getOrderId(), primaryReservation.getReservationId(), String.format("WMS transport failed: %s", ex.getMessage()));
                    });
                    return null;
                });
    }
}
