package com.example.order.application.service;

import com.example.order.application.domain.SagaCompensationRequiredEvent;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.SagaLogEntry;
import com.example.order.application.port.out.SagaLogPort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class SagaTimeoutDetector {

    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;

    public SagaTimeoutDetector(SagaLogPort sagaLogPort, DomainEventPublisher eventPublisher) {
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.saga.timeout.check-interval:60000}")
    public void detectTimeouts() {
        Duration timeout = Duration.ofSeconds(300); // default 5 minutes
        List<SagaLogEntry> pending = sagaLogPort.findPendingStepsOlderThan(timeout);

        for (SagaLogEntry entry : pending) {
            sagaLogPort.recordSagaCompensationRequired(
                entry.orderId(), entry.stepName(), "TIMEOUT"
            );

            eventPublisher.publish(new SagaCompensationRequiredEvent(
                entry.orderId(), entry.stepName(), "TIMEOUT", List.of()
            ));
        }
    }
}
