package com.example.order.infrastructure.adapter;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.port.out.ConfirmReservationCommand;
import com.example.order.application.port.out.InventoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InventoryConfirmationSchedulerAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class InventoryConfirmationSchedulerAdapterTest {

    @Mock
    private InventoryPort inventoryPort;

    private InventoryConfirmationSchedulerAdapter scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InventoryConfirmationSchedulerAdapter(inventoryPort, 0);
    }

    @Test
    @DisplayName("should schedule confirmation and call inventoryPort.confirm after delay")
    void shouldScheduleConfirmation() throws InterruptedException {
        InventoryReservation reservation = InventoryReservation.pending("SKU-1", 5, "ord-1");
        when(inventoryPort.confirm(any(ConfirmReservationCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.scheduleConfirmation(reservation);

        // Wait for the scheduled task to execute (delay=0 so it should run immediately)
        Thread.sleep(200);

        ArgumentCaptor<ConfirmReservationCommand> captor =
                ArgumentCaptor.forClass(ConfirmReservationCommand.class);
        verify(inventoryPort).confirm(captor.capture());
        assertThat(captor.getValue().getReservationId()).isEqualTo(reservation.getReservationId());
    }

    @Test
    @DisplayName("should handle confirmation failure gracefully")
    void shouldHandleConfirmationFailure() throws InterruptedException {
        InventoryReservation reservation = InventoryReservation.pending("SKU-2", 3, "ord-2");
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("confirmation failed"));
        when(inventoryPort.confirm(any(ConfirmReservationCommand.class)))
                .thenReturn(failed);

        scheduler.scheduleConfirmation(reservation);

        // Wait for the scheduled task to execute
        Thread.sleep(200);

        verify(inventoryPort).confirm(any(ConfirmReservationCommand.class));
        // No exception should propagate — error is logged
    }

    @Test
    @DisplayName("should reject null inventoryPort in constructor")
    void shouldRejectNullInventoryPort() {
        assertThatThrownBy(() -> new InventoryConfirmationSchedulerAdapter(null, 60))
                .isInstanceOf(NullPointerException.class);
    }
}
