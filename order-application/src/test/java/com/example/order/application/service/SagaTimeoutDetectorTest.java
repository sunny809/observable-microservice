package com.example.order.application.service;

import com.example.order.application.domain.SagaCompensationRequiredEvent;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.SagaLogEntry;
import com.example.order.application.port.out.SagaLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SagaTimeoutDetectorTest {

    @Mock
    private SagaLogPort sagaLogPort;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private SagaTimeoutDetector detector;

    @Test
    @DisplayName("should detect timeout and publish compensation event")
    void shouldDetectTimeoutAndPublishCompensationEvent() {
        // given
        SagaLogEntry entry = new SagaLogEntry(1L, "order-123", "WMS_ACKED", "PENDING",
            LocalDateTime.now().minusMinutes(10), null);
        when(sagaLogPort.findPendingStepsOlderThan(any(Duration.class)))
            .thenReturn(List.of(entry));

        // when
        detector.detectTimeouts();

        // then
        verify(sagaLogPort).recordSagaCompensationRequired("order-123", "WMS_ACKED", "TIMEOUT");
        verify(eventPublisher).publish(any(SagaCompensationRequiredEvent.class));
    }

    @Test
    @DisplayName("should do nothing when no pending steps")
    void shouldDoNothingWhenNoPendingSteps() {
        // given
        when(sagaLogPort.findPendingStepsOlderThan(any(Duration.class)))
            .thenReturn(List.of());

        // when
        detector.detectTimeouts();

        // then
        verify(sagaLogPort, never()).recordSagaCompensationRequired(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }
}
