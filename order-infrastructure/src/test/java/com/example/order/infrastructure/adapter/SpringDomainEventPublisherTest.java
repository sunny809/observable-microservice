package com.order.demo.infrastructure.adapter;

import com.order.demo.application.domain.InventoryReservation;
import com.order.demo.application.domain.ReservationStatus;
import com.order.demo.application.domain.WmsInstructionRequiredEvent;
import com.order.demo.application.port.out.WmsShipmentInstruction;
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
