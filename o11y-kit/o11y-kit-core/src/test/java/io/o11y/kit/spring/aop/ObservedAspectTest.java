package io.o11y.kit.spring.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ObservedAspect}.
 *
 * <p>Uses mocked {@link ProceedingJoinPoint} to verify metric recording
 * behavior in isolation from the Spring AOP container.
 */
@ExtendWith(MockitoExtension.class)
class ObservedAspectTest {

    private MeterRegistry meterRegistry;
    private ObservedAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aspect = new ObservedAspect(meterRegistry);
    }

    @Test
    @DisplayName("should record single timer with outcome=success for successful method")
    void shouldRecordTimerWithSuccessOutcomeForSuccessfulMethod() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("basicMethod"));
        when(methodSignature.getName()).thenReturn("basicMethod");
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.observeAndRecord(joinPoint);

        assertThat(result).isEqualTo("ok");
        Timer timer = meterRegistry.find("o11y.observed.duration")
                .tags("class", "TargetService", "method", "basicMethod",
                        "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        // Verify NO timer exists without the outcome tag (no double-recording)
        Collection<Timer> allTimers = meterRegistry.find("o11y.observed.duration").timers();
        assertThat(allTimers).hasSize(1);
    }

    @Test
    @DisplayName("should record outcome=error when method throws Exception")
    void shouldRecordErrorOutcomeWhenMethodThrowsException() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("throwsException"));
        when(methodSignature.getName()).thenReturn("throwsException");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("expected error"));

        assertThatThrownBy(() -> aspect.observeAndRecord(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("expected error");

        Timer errorTimer = meterRegistry.find("o11y.observed.duration")
                .tags("class", "TargetService", "method", "throwsException",
                        "outcome", "error")
                .timer();
        assertThat(errorTimer).isNotNull();
        assertThat(errorTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should record outcome=error when method throws Error")
    void shouldRecordErrorOutcomeWhenMethodThrowsError() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("basicMethod"));
        when(methodSignature.getName()).thenReturn("basicMethod");
        when(joinPoint.proceed()).thenThrow(new StackOverflowError("stack overflow"));

        assertThatThrownBy(() -> aspect.observeAndRecord(joinPoint))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("stack overflow");

        Timer errorTimer = meterRegistry.find("o11y.observed.duration")
                .tags("class", "TargetService", "method", "basicMethod",
                        "outcome", "error")
                .timer();
        assertThat(errorTimer).isNotNull();
        assertThat(errorTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should use custom metric name when specified")
    void shouldUseCustomMetricName() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("customName"));
        when(methodSignature.getName()).thenReturn("customName");
        when(joinPoint.proceed()).thenReturn("custom");

        aspect.observeAndRecord(joinPoint);

        Timer timer = meterRegistry.find("custom.test.metric")
                .tags("class", "TargetService", "method", "customName",
                        "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should include custom tags from annotation")
    void shouldIncludeCustomTags() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("withTags"));
        when(methodSignature.getName()).thenReturn("withTags");
        when(joinPoint.proceed()).thenReturn("tagged");

        aspect.observeAndRecord(joinPoint);

        Timer timer = meterRegistry.find("o11y.observed.duration")
                .tags("class", "TargetService", "method", "withTags",
                        "region", "us-east-1", "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject odd-length tags array")
    void shouldRejectOddLengthTags() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("oddTags"));
        when(methodSignature.getName()).thenReturn("oddTags");

        assertThatThrownBy(() -> aspect.observeAndRecord(joinPoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-value pairs");
    }

    @Test
    @DisplayName("should use custom description when specified")
    void shouldUseCustomDescription() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getTarget()).thenReturn(new TargetService());
        when(methodSignature.getMethod()).thenReturn(
                TargetService.class.getMethod("withDescription"));
        when(methodSignature.getName()).thenReturn("withDescription");
        when(joinPoint.proceed()).thenReturn("described");

        aspect.observeAndRecord(joinPoint);

        Timer timer = meterRegistry.find("o11y.observed.duration")
                .tags("class", "TargetService", "method", "withDescription",
                        "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getDescription()).isEqualTo("Custom method description");
    }

    @Test
    @DisplayName("should reject null MeterRegistry")
    void shouldRejectNullMeterRegistry() {
        assertThatThrownBy(() -> new ObservedAspect(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MeterRegistry must not be null");
    }

    // --- Target service with @Observed methods (used by mock via reflection) ---

    static class TargetService {
        @Observed
        public String basicMethod() { return "ok"; }

        @Observed
        public String throwsException() { throw new RuntimeException("expected error"); }

        @Observed(name = "custom.test.metric")
        public String customName() { return "custom"; }

        @Observed(tags = {"region", "us-east-1", "env", "prod"})
        public String withTags() { return "tagged"; }

        @Observed(tags = {"lonely"})
        public String oddTags() { return "bad"; }

        @Observed(description = "Custom method description")
        public String withDescription() { return "described"; }
    }
}
