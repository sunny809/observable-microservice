package com.example.order.infrastructure.adapter;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.port.out.ConfirmReservationCommand;
import com.example.order.application.port.out.InventoryPort;
import com.example.order.application.port.out.InventoryConfirmationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a delayed inventory confirmation as a safety net after WMS
 * instruction is sent. If the WMS does not respond within the configured
 * delay, the confirmation is attempted proactively to prevent reservations
 * from remaining in PENDING state indefinitely.
 *
 * <p>The confirmation delay is configurable via
 * {@code app.inventory.confirmation-delay-seconds} (default: 60).
 */
@Component
public class InventoryConfirmationSchedulerAdapter implements InventoryConfirmationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryConfirmationSchedulerAdapter.class);

    private final InventoryPort inventoryPort;
    private final ScheduledExecutorService scheduler;
    private final long confirmationDelaySeconds;

    public InventoryConfirmationSchedulerAdapter(
            InventoryPort inventoryPort,
            @Value("${app.inventory.confirmation-delay-seconds:60}") long confirmationDelaySeconds) {
        this.inventoryPort = inventoryPort;
        this.confirmationDelaySeconds = confirmationDelaySeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "inventory-confirmation-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void scheduleConfirmation(InventoryReservation reservation) {
        log.info("Scheduling inventory confirmation for reservation {} in {}s",
                reservation.getReservationId(), confirmationDelaySeconds);

        scheduler.schedule(() -> {
            try {
                log.info("Executing scheduled inventory confirmation for reservation {}",
                        reservation.getReservationId());
                inventoryPort.confirm(new ConfirmReservationCommand(reservation.getReservationId()))
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Scheduled inventory confirmation failed for reservation {}",
                                        reservation.getReservationId(), ex);
                            } else {
                                log.info("Scheduled inventory confirmation completed for reservation {}",
                                        reservation.getReservationId());
                            }
                        });
            } catch (Exception e) {
                log.error("Error scheduling inventory confirmation for reservation {}",
                        reservation.getReservationId(), e);
            }
        }, confirmationDelaySeconds, TimeUnit.SECONDS);
    }
}
