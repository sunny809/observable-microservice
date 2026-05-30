package com.example.order.infrastructure.adapter;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.ReservationStatus;
import com.example.order.application.domain.WmsInstructionRequiredEvent;
import com.example.order.application.port.out.WmsShipmentInstruction;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.mockito.Mockito.*;

class SpringDomainEventPublisherTest {

    @Test
    void testPublishDelegatesToApplicationEventPublisher() {
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(mockPublisher);

        WmsInstructionRequiredEvent event = new WmsInstructionRequiredEvent(
                "ord-1",
                new WmsShipmentInstruction("ord-1", "resv-1"),
                new InventoryReservation("resv-1", "SKU-1", 5, "ord-1",
                        ReservationStatus.PENDING, Instant.now(), null));

        publisher.publish(event);

        verify(mockPublisher).publishEvent(event);
    }
}
