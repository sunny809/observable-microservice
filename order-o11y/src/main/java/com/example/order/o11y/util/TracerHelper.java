package com.example.order.o11y.util;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public final class TracerHelper {

    private TracerHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Tracer getTracer() {
        return GlobalOpenTelemetry.getTracer("order-service");
    }

    public static final class SpanNames {
        public static final String ORDER_PLACEMENT = "order.placement";
        public static final String INVENTORY_OCCUPY = "inventory.occupy";
        public static final String INVENTORY_RELEASE = "inventory.release";
        public static final String INVENTORY_CONFIRM = "inventory.confirm";
        public static final String WMS_SEND = "wms.send_instruction";

        private SpanNames() {
            throw new UnsupportedOperationException("Utility class");
        }
    }
}
