package com.example.order.application.service;

import com.example.order.application.domain.SagaCompensationRequiredEvent;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.SagaLogEntry;
import com.example.order.application.port.out.SagaLogPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
public class SagaTimeoutDetector {

    private final SagaLogPort sagaLogPort;
    private final DomainEventPublisher eventPublisher;
    private final Duration defaultTimeout;

    public SagaTimeoutDetector(SagaLogPort sagaLogPort,
                               DomainEventPublisher eventPublisher,
                               @Value("${app.saga.timeout.default-timeout:300s}") Duration defaultTimeout) {
        this.sagaLogPort = sagaLogPort;
        this.eventPublisher = eventPublisher;
        this.defaultTimeout = defaultTimeout;
    }

    @Scheduled(fixedDelayString = "${app.saga.timeout.check-interval:60000}")
    @Transactional
    public void detectTimeouts() {
        List<SagaLogEntry> pending = sagaLogPort.findPendingStepsOlderThan(defaultTimeout);

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