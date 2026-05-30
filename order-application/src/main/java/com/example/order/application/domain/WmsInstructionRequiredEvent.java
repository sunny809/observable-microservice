package com.example.order.application.domain;

import com.example.order.application.port.out.WmsShipmentInstruction;
import java.util.List;
import java.util.Objects;

public class WmsInstructionRequiredEvent {
    private final String orderId;
    private final WmsShipmentInstruction instruction;
    private final List<InventoryReservation> reservations;

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
