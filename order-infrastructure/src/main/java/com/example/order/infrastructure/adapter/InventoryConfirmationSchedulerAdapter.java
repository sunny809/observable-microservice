package com.example.order.infrastructure.adapter;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.port.out.InventoryConfirmationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryConfirmationSchedulerAdapter implements InventoryConfirmationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InventoryConfirmationSchedulerAdapter.class);

    @Override
    public void scheduleConfirmation(InventoryReservation reservation) {
        logger.info("Scheduling inventory confirmation for reservation {}", reservation.getReservationId());
        // TODO: Send confirmation message to a message queue or local scheduling service
    }
}
