package com.example.order.adapter.outbound.cache;

import com.example.order.application.port.out.IdempotencyCachePort;
import org.springframework.stereotype.Component;

/**
 * Adapter that implements the IdempotencyCachePort using Caffeine cache.
 */
@Component
public class IdempotencyCacheAdapter implements IdempotencyCachePort {

    private final IdempotencyKeyCache cache;

    public IdempotencyCacheAdapter(IdempotencyKeyCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean exists(String key) {
        return cache.exists(key);
    }

    @Override
    public void put(String key) {
        cache.put(key);
    }

    @Override
    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
