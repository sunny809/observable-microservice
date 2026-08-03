package com.order.demo.application.domain;

import com.order.demo.application.port.out.WmsShipmentInstruction;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain event published when an order requires a WMS shipment instruction.
 *
 * <p>Published by {@link com.order.demo.application.service.OrderPlacementSaga}
 * after the order transaction commits. Consumed asynchronously by
 * {@code @TransactionalEventListener} to send the instruction to the WMS service
 * without blocking the HTTP response.
 *
 * <p>All fields are immutable (defensive copies are made for collections).
 *
 * @see com.order.demo.application.service.OrderPlacementSaga#placeOrder
 * @see com.order.demo.application.service.OrderPlacementSaga#onWmsRequired
 */
public class WmsInstructionRequiredEvent {
    private final String orderId;
    private final WmsShipmentInstruction instruction;
    private final List<InventoryReservation> reservations;
    private final Instant emittedAt;

    /**
     * Creates a new event with multiple reservations.
     *
     * @param orderId the order ID
     * @param instruction the WMS shipment instruction
     * @param reservations the list of inventory reservations (must not be empty)
     * @throws IllegalArgumentException if reservations is empty
     */
    public WmsInstructionRequiredEvent(String orderId,
                                       WmsShipmentInstruction instruction,
                                       List<InventoryReservation> reservations) {
        this(orderId, instruction, reservations, Instant.now());
    }

    /**
     * Creates a new event with multiple reservations and an explicit emission time.
     *
     * <p>Used by the saga to measure the {@code POST_COMMIT_TO_WMS} idle gap: the
     * listener compares {@code emittedAt} against the moment it starts handling the event.
     *
     * @param orderId the order ID
     * @param instruction the WMS shipment instruction
     * @param reservations the list of inventory reservations (must not be empty)
     * @param emittedAt the wall-clock time at which the event was emitted
     * @throws IllegalArgumentException if reservations is empty
     */
    public WmsInstructionRequiredEvent(String orderId,
                                       WmsShipmentInstruction instruction,
                                       List<InventoryReservation> reservations,
                                       Instant emittedAt) {
        this.orderId = Objects.requireNonNull(orderId);
        this.instruction = Objects.requireNonNull(instruction);
        this.reservations = List.copyOf(Objects.requireNonNull(reservations));
        this.emittedAt = Objects.requireNonNull(emittedAt);
        if (reservations.isEmpty()) {
            throw new IllegalArgumentException("reservations must not be empty");
        }
    }

    /**
     * Creates a new event with a single reservation.
     *
     * @param orderId the order ID
     * @param instruction the WMS shipment instruction
     * @param reservation the single inventory reservation
     */
    public WmsInstructionRequiredEvent(String orderId,
                                       WmsShipmentInstruction instruction,
                                       InventoryReservation reservation) {
        this(orderId, instruction, List.of(Objects.requireNonNull(reservation)));
    }

    public String getOrderId() {
        return orderId;
    }

    public WmsShipmentInstruction getInstruction() {
        return instruction;
    }

    public InventoryReservation getReservation() {
        return reservations.get(0);
    }

    public List<InventoryReservation> getReservations() {
        return reservations;
    }

    /**
     * @return the wall-clock time at which this event was emitted, used for
     *         measuring the {@code POST_COMMIT_TO_WMS} gap
     */
    public Instant getEmittedAt() {
        return emittedAt;
    }
}
