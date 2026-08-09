package com.order.demo.adapter.outbound.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class IdempotencyCacheAdapterTest {

    private IdempotencyKeyCache cache;
    private IdempotencyCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        cache = mock(IdempotencyKeyCache.class);
        adapter = new IdempotencyCacheAdapter(cache);
    }

    @Test
    @DisplayName("exists should delegate to the underlying cache")
    void testExistsDelegates() {
        when(cache.exists("idem-key-1")).thenReturn(true);

        assertTrue(adapter.exists("idem-key-1"));
        verify(cache).exists("idem-key-1");
    }

    @Test
    @DisplayName("put should delegate to the underlying cache")
    void testPutDelegates() {
        adapter.put("idem-key-1");

        verify(cache).put("idem-key-1");
        verify(cache, never()).exists(anyString());
    }

    @Test
    @DisplayName("invalidate should delegate to the underlying cache")
    void testInvalidateDelegates() {
        adapter.invalidate("idem-key-1");

        verify(cache).invalidate("idem-key-1");
    }
}
