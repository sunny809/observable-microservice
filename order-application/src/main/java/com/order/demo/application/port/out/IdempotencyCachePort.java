package com.order.demo.application.port.out;

/**
 * Port for idempotency key caching to reduce database lookups.
 *
 * <p>Provides a fast-path cache check before hitting the database
 * for duplicate order detection. The cache is optional - a miss
 * should still trigger a database lookup for correctness.
 */
public interface IdempotencyCachePort {

    /**
     * Checks if the idempotency key exists in the cache.
     *
     * @param key the idempotency key to check
     * @return true if the key exists in cache, false otherwise
     */
    boolean exists(String key);

    /**
     * Adds an idempotency key to the cache after successful order creation.
     *
     * @param key the idempotency key to cache
     */
    void put(String key);

    /**
     * Removes an idempotency key from the cache.
     *
     * @param key the idempotency key to remove
     */
    void invalidate(String key);
}