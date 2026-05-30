package com.example.order.o11y.util;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class TracerHelperTest {

    @Test
    void testSpanNamesConstantsHaveExpectedValues() {
        assertEquals("order.placement", TracerHelper.SpanNames.ORDER_PLACEMENT);
        assertEquals("inventory.occupy", TracerHelper.SpanNames.INVENTORY_OCCUPY);
        assertEquals("inventory.release", TracerHelper.SpanNames.INVENTORY_RELEASE);
        assertEquals("wms.send_instruction", TracerHelper.SpanNames.WMS_SEND);
    }

    @Test
    void testGetTracerReturnsNonNullOrSkipsWithoutSetup() {
        // Without a real OpenTelemetry setup, GlobalOpenTelemetry.getTracer()
        // returns a no-op tracer. We just verify it doesn't throw.
        assertDoesNotThrow(() -> {
            Tracer tracer = TracerHelper.getTracer();
            assertNotNull(tracer);
        });
    }

    @Test
    void testPrivateConstructorCannotBeInstantiated() throws Exception {
        Constructor<TracerHelper> constructor = TracerHelper.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof UnsupportedOperationException);
    }

    @Test
    void testSpanNamesPrivateConstructorCannotBeInstantiated() throws Exception {
        Constructor<TracerHelper.SpanNames> constructor = TracerHelper.SpanNames.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof UnsupportedOperationException);
    }
}
