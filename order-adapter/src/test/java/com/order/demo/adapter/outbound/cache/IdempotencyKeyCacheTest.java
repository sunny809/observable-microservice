package com.order.demo.adapter.outbound.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class IdempotencyKeyCacheTest {

    private IdempotencyKeyCache cache;

    @BeforeEach
    void setUp() {
        cache = new IdempotencyKeyCache();
    }

    @Test
    @DisplayName("exists should return false for a key never put")
    void testExistsReturnsFalseForMissingKey() {
        assertFalse(cache.exists("unknown-key"), "cache miss should return false");
    }

    @Test
    @DisplayName("exists should return true after put")
    void testExistsReturnsTrueAfterPut() {
        cache.put("idem-key-1");

        assertTrue(cache.exists("idem-key-1"), "cache hit after put should return true");
    }

    @Test
    @DisplayName("put is idempotent — putting the same key twice keeps it present")
    void testPutIdempotent() {
        cache.put("idem-key-1");
        cache.put("idem-key-1");

        assertTrue(cache.exists("idem-key-1"));
    }

    @Test
    @DisplayName("invalidate should remove a cached key")
    void testInvalidateRemovesKey() {
        cache.put("idem-key-1");
        assertTrue(cache.exists("idem-key-1"));

        cache.invalidate("idem-key-1");

        assertFalse(cache.exists("idem-key-1"), "key should be gone after invalidation");
    }

    @Test
    @DisplayName("invalidate on a missing key should be a no-op")
    void testInvalidateMissingKeyIsNoOp() {
        assertDoesNotThrow(() -> cache.invalidate("never-existed"));
        assertFalse(cache.exists("never-existed"));
    }

    @Test
    @DisplayName("clear should remove all cached keys")
    void testClearRemovesAll() {
        cache.put("idem-key-1");
        cache.put("idem-key-2");
        assertTrue(cache.exists("idem-key-1"));
        assertTrue(cache.exists("idem-key-2"));

        cache.clear();

        assertFalse(cache.exists("idem-key-1"), "all keys should be gone after clear");
        assertFalse(cache.exists("idem-key-2"), "all keys should be gone after clear");
    }
}
