/**
 * Saga orchestration for the order placement flow.
 *
 * <p>The {@link com.order.demo.application.service.OrderPlacementSaga}
 * coordinates inventory reservation, order persistence, and WMS instruction
 * sending with compensating transactions on failure.
 */
package com.order.demo.application.service;
