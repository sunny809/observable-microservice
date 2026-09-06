package com.order.demo.application.port.in;

/**
 * Inbound port for cancelling an order.
 *
 * <p>Implemented by {@code OrderCancellationSaga}, which compensates the
 * placement saga: releases reserved inventory, voids any WMS instruction,
 * and transitions the order to {@code CANCELLED}.
 */
public interface CancelOrderUseCase {

    /**
     * Cancels an order that has not yet been dispatched.
     *
     * @param command the cancellation command containing order ID, reason, and customer ID
     * @return the cancellation result
     * @throws com.order.demo.application.domain.OrderNotFoundException if the order does not exist
     * @throws IllegalStateException if the order is not in a cancellable state, or the
     *                               customer does not own the order
     */
    OrderCancelledResult cancel(CancelOrderCommand command);
}
