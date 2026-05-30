package com.example.order.adapter.outbound.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * In-memory cache for idempotency keys to reduce database lookups.
 *
 * <p>Uses Caffeine cache with time-based expiration to handle the common case
 * of duplicate request detection. The cache is backed by database persistence
 * for consistency - the cache serves as a fast path filter.
 *
 * <p><strong>Note:</strong> This cache does not guarantee 100% consistency with
 * the database. A cache miss will still trigger a database lookup, ensuring
 * correctness. The cache only provides a performance optimization for the
 * common case where the same idempotency key is reused.
 */
@Component
public class IdempotencyKeyCache {

    private final Cache<String, String> cache;

    public IdempotencyKeyCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(10_000)
                .build();
    }

    /**
     * Checks if the idempotency key exists in the cache.
     *
     * @param key the idempotency key to check
     * @return true if the key exists in cache, false otherwise
     */
    public boolean exists(String key) {
        return cache.getIfPresent(key) != null;
    }

    /**
     * Adds an idempotency key to the cache after successful order creation.
     *
     * @param key the idempotency key to cache
     */
    public void put(String key) {
        cache.put(key, key);
    }

    /**
     * Removes an idempotency key from the cache.
     * Used for testing or when an order is cancelled/deleted.
     *
     * @param key the idempotency key to remove
     */
    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
