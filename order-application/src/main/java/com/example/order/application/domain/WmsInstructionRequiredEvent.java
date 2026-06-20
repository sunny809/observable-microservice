package com.example.order.application.domain;

import com.example.order.application.port.out.WmsShipmentInstruction;
import java.util.List;
import java.util.Objects;

/**
 * Domain event published when an order requires a WMS shipment instruction.
 *
 * <p>Published by {@link com.example.order.application.service.OrderPlacementSaga}
 * after the order transaction commits. Consumed asynchronously by
 * {@code @TransactionalEventListener} to send the instruction to the WMS service
 * without blocking the HTTP response.
 *
 * <p>All fields are immutable (defensive copies are made for collections).
 *
 * @see com.example.order.application.service.OrderPlacementSaga#placeOrder
 * @see com.example.order.application.service.OrderPlacementSaga#onWmsRequired
 */
public class WmsInstructionRequiredEvent {
    private final String orderId;
    private final WmsShipmentInstruction instruction;
    private final List<InventoryReservation> reservations;

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
        this.orderId = Objects.requireNonNull(orderId);
        this.instruction = Objects.requireNonNull(instruction);
        this.reservations = List.copyOf(Objects.requireNonNull(reservations));
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
}
