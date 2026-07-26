package com.order.demo.application.domain;

import java.util.List;

public class SagaCompensationRequiredEvent {
    private final String orderId;
    private final String stepName;
    private final String reason;
    private final List<InventoryReservation> reservations;

    public SagaCompensationRequiredEvent(String orderId, String stepName, String reason, List<InventoryReservation> reservations) {
        this.orderId = orderId;
        this.stepName = stepName;
        this.reason = reason;
        this.reservations = reservations;
    }

    public String getOrderId() { return orderId; }
    public String getStepName() { return stepName; }
    public String getReason() { return reason; }
    public List<InventoryReservation> getReservations() { return reservations; }
}
