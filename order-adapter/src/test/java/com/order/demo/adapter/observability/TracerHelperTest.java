package com.order.demo.adapter.observability;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TracerHelperTest {

    @Test
    @DisplayName("getTracer should return a non-null tracer")
    void testGetTracerReturnsNonNull() {
        Tracer tracer = TracerHelper.getTracer();

        assertNotNull(tracer, "GlobalOpenTelemetry should provide a tracer instance");
    }

    @Test
    @DisplayName("SpanNames constants should be accessible and carry meaningful names")
    void testSpanNamesConstants() {
        assertEquals("order.placement", TracerHelper.SpanNames.ORDER_PLACEMENT);
        assertEquals("inventory.occupy", TracerHelper.SpanNames.INVENTORY_OCCUPY);
        assertEquals("inventory.release", TracerHelper.SpanNames.INVENTORY_RELEASE);
        assertEquals("inventory.confirm", TracerHelper.SpanNames.INVENTORY_CONFIRM);
        assertEquals("wms.send_instruction", TracerHelper.SpanNames.WMS_SEND);
        assertEquals("wms.picking_complete", TracerHelper.SpanNames.WMS_PICKING_COMPLETE);
        assertEquals("tms.send_instruction", TracerHelper.SpanNames.TMS_SEND);
    }

    @Test
    @DisplayName("TracerHelper private constructor throws — utility class cannot be instantiated")
    void testPrivateConstructorThrows() throws Exception {
        Constructor<TracerHelper> constructor = TracerHelper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause(),
                "utility class constructor must throw UnsupportedOperationException");
    }

    @Test
    @DisplayName("SpanNames private constructor throws — utility class cannot be instantiated")
    void testSpanNamesPrivateConstructorThrows() throws Exception {
        Constructor<TracerHelper.SpanNames> constructor =
                TracerHelper.SpanNames.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause(),
                "utility class constructor must throw UnsupportedOperationException");
    }
}
