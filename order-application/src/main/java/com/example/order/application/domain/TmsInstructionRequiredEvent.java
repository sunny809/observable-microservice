package com.example.order.application.domain;

import com.example.order.application.port.out.TmsShipmentInstruction;
import java.util.List;
import java.util.Objects;

/**
 * Domain event published when a TMS dispatch instruction needs to be sent.
 *
 * <p>Published by {@link com.example.order.application.service.OrderPlacementSaga}
 * after WMS picking is complete. Consumed asynchronously by
 * {@code @TransactionalEventListener} to send the instruction to the TMS service.
 *
 * <p>All fields are immutable (defensive copies are made for collections).
 *
 * @see com.example.order.application.service.OrderPlacementSaga#onTmsRequired
 */
public class TmsInstructionRequiredEvent {
    private final String orderId;
    private final TmsShipmentInstruction instruction;
    private final List<InventoryReservation> reservations;

    /**
     * Creates a new event with multiple reservations.
     *
     * @param orderId the order ID
     * @param instruction the TMS dispatch instruction
     * @param reservations the list of inventory reservations
     */
    public TmsInstructionRequiredEvent(String orderId,
                                     TmsShipmentInstruction instruction,
                                     List<InventoryReservation> reservations) {
        this.orderId = Objects.requireNonNull(orderId);
        this.instruction = Objects.requireNonNull(instruction);
        this.reservations = List.copyOf(Objects.requireNonNull(reservations));
    }

    public String getOrderId() {
        return orderId;
    }

    public TmsShipmentInstruction getInstruction() {
        return instruction;
    }

    public List<InventoryReservation> getReservations() {
        return reservations;
    }
}
