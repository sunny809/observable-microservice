package io.o11y.kit.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TraceIdResolver}.
 */
class TraceIdResolverTest {

    @Test
    void shouldResolveB3TraceId() {
        String traceId = TraceIdResolver.resolve(name -> {
            if ("X-B3-TraceId".equals(name)) return "abc123";
            return null;
        });
        assertEquals("abc123", traceId);
    }

    @Test
    void shouldResolveW3CTraceparent() {
        String traceId = TraceIdResolver.resolve(name -> {
            if ("traceparent".equals(name)) return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
            return null;
        });
        assertEquals("0af7651916cd43dd8448eb211c80319c", traceId);
    }

    @Test
    void shouldFallbackToRandomUuid() {
        String traceId = TraceIdResolver.resolve(name -> null);
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
    }

    @Test
    void shouldPreferB3OverW3C() {
        String traceId = TraceIdResolver.resolve(name -> {
            if ("X-B3-TraceId".equals(name)) return "b3-trace";
            if ("traceparent".equals(name)) return "00-w3c-trace-span-01";
            return null;
        });
        assertEquals("b3-trace", traceId);
    }

    @Test
    void shouldHandleBlankB3Header() {
        String traceId = TraceIdResolver.resolve(name -> {
            if ("X-B3-TraceId".equals(name)) return "   ";
            if ("traceparent".equals(name)) return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
            return null;
        });
        // Should fall through to W3C since B3 is blank
        assertEquals("0af7651916cd43dd8448eb211c80319c", traceId);
    }
}