package com.order.demo.application.port.in;

import com.order.demo.application.domain.Order;
import java.util.List;

/**
 * Inbound port for placing orders.
 *
 * <p>Primary use case interface that the REST controller calls to initiate
 * the order placement saga. Implementations (such as {@code OrderPlacementSaga})
 * orchestrate the distributed transaction across inventory, order persistence,
 * and WMS services.
 *
 * @see com.order.demo.application.service.OrderPlacementSaga
 */
public interface PlaceOrderUseCase {

    /**
     * Places a new order, triggering the order placement saga.
     *
     * @param command the place order command containing customer, items, and idempotency key
     * @return the result containing the generated order ID and initial status
     * @throws com.order.demo.application.domain.DuplicateOrderException if the idempotency key already exists
     * @throws com.order.demo.application.domain.InsufficientInventoryException if any item cannot be reserved
     */
    OrderPlacedResult placeOrder(PlaceOrderCommand command);

    /**
     * Finds all orders containing an item with the given SKU.
     *
     * @param sku the SKU to search for
     * @return list of orders containing the SKU
     */
    List<Order> findBySku(String sku);
}
