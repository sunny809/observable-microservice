package com.example.order.adapter.inbound.rest.aop;

import com.example.order.o11y.util.TracerHelper;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {

    private static final Logger log = LoggerFactory.getLogger(TraceAspect.class);
    private Tracer tracer;

    public TraceAspect() {
        this.tracer = TracerHelper.getTracer();
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(com.example.order.adapter.inbound.rest.aop.Traced)")
    public Object aroundTracedMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Traced traced = signature.getMethod().getAnnotation(Traced.class);
        String spanName = traced != null ? traced.spanName() : signature.getName();
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            span.recordException(ex);
            log.error("Exception in traced span {}", spanName, ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}
