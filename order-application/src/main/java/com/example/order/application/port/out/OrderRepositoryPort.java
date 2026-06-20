package com.example.order.application.port.out;

import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import java.util.Optional;

/**
 * Outbound port for order persistence operations.
 *
 * <p>Abstracts the storage mechanism for {@link Order} aggregates.
 * Implementations (such as {@code OrderPersistenceAdapter}) handle the
 * actual database operations, allowing the domain layer to remain
 * persistence-agnostic.
 *
 * @see com.example.order.adapter.outbound.persistence.OrderPersistenceAdapter
 */
public interface OrderRepositoryPort {

    /**
     * Persists a new order.
     *
     * @param order the order to save
     */
    void save(Order order);

    /**
     * Finds an order by its unique ID.
     *
     * @param orderId the order ID
     * @return the order, or empty if not found
     */
    Optional<Order> findById(String orderId);

    /**
     * Finds an order by its idempotency key.
     *
     * @param idempotencyKey the idempotency key
     * @return the order, or empty if not found
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Updates the status of an existing order.
     *
     * @param orderId the order ID
     * @param status the new status
     */
    void updateStatus(String orderId, OrderStatus status);
}
