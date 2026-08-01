package com.order.demo.adapter.inbound.rest.aop;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

class TracedTest {

    @Test
    void testAnnotationHasRuntimeRetention() {
        Retention retention = Traced.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void testAnnotationTargetsMethodAndType() {
        Target target = Traced.class.getAnnotation(Target.class);
        assertNotNull(target);
        assertTrue(java.util.Set.of(target.value()).containsAll(
                java.util.List.of(java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE)));
    }

    @Test
    void testDefaultSpanNameIsOperation() throws NoSuchMethodException {
        var method = Traced.class.getMethod("spanName");
        // AnnotationElement has a default value for the element
        assertEquals("operation", method.getDefaultValue());
    }
}
