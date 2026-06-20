package com.example.order.application.port.in;

/**
 * Inbound port for placing orders.
 *
 * <p>Primary use case interface that the REST controller calls to initiate
 * the order placement saga. Implementations (such as {@code OrderPlacementSaga})
 * orchestrate the distributed transaction across inventory, order persistence,
 * and WMS services.
 *
 * @see com.example.order.application.service.OrderPlacementSaga
 */
public interface PlaceOrderUseCase {

    /**
     * Places a new order, triggering the order placement saga.
     *
     * @param command the place order command containing customer, items, and idempotency key
     * @return the result containing the generated order ID and initial status
     * @throws com.example.order.application.domain.DuplicateOrderException if the idempotency key already exists
     * @throws com.example.order.application.domain.InsufficientInventoryException if any item cannot be reserved
     */
    OrderPlacedResult placeOrder(PlaceOrderCommand command);
}
