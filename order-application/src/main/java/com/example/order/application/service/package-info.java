/**
 * Saga orchestration for the order placement flow.
 *
 * <p>The {@link com.example.order.application.service.OrderPlacementSaga}
 * coordinates inventory reservation, order persistence, and WMS instruction
 * sending with compensating transactions on failure.
 */
package com.example.order.application.service;
