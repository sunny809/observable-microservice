package com.example.order.adapter.inbound.rest.aop;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class TraceAspectTest {

    private Tracer tracer;
    private SpanBuilder spanBuilder;
    private Span span;
    private Scope scope;
    private TraceAspect aspect;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        spanBuilder = mock(SpanBuilder.class);
        span = mock(Span.class);
        scope = mock(Scope.class);

        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);

        aspect = new TraceAspect();
        aspect.setTracer(tracer);
    }

    @Test
    @DisplayName("aroundTracedMethod should create span and return result")
    void testAroundTracedMethodCreatesSpan() throws Throwable {
        TestController controller = new TestController();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getTarget()).thenReturn(controller);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(TestController.class.getMethod("tracedMethod", String.class));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundTracedMethod(joinPoint);

        assertEquals("result", result);
        verify(tracer).spanBuilder("custom.span.name");
        verify(spanBuilder).startSpan();
        verify(span).makeCurrent();
        verify(span).end();
        verify(scope).close();
    }

    @Test
    @DisplayName("aroundTracedMethod should set error status on exception")
    void testAroundTracedMethodSetsErrorOnException() throws Throwable {
        TestController controller = new TestController();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getTarget()).thenReturn(controller);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(TestController.class.getMethod("tracedMethod", String.class));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("boom"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> aspect.aroundTracedMethod(joinPoint));
        assertEquals("boom", ex.getMessage());

        verify(span).setStatus(StatusCode.ERROR, "boom");
        verify(span).recordException(any(RuntimeException.class));
        verify(span).end();
    }

    @Test
    @DisplayName("aroundTracedMethod should use method name as default span name when annotation missing")
    void testAroundTracedMethodUsesDefaultSpanName() throws Throwable {
        TestController controller = new TestController();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getTarget()).thenReturn(controller);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(TestController.class.getMethod("defaultSpanMethod"));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.aroundTracedMethod(joinPoint);

        assertEquals("ok", result);
        verify(tracer).spanBuilder("default.span");
    }

    static class TestController {
        @Traced(spanName = "custom.span.name")
        public String tracedMethod(String input) {
            return "result";
        }

        @Traced(spanName = "default.span")
        public String defaultSpanMethod() {
            return "ok";
        }
    }
}
