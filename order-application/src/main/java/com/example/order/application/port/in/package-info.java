/**
 * Inbound ports (driving adapters) for the order application.
 *
 * <p>Defines the use cases that external actors (such as REST controllers)
 * can invoke. These interfaces are implemented by the application layer
 * and called by the adapter layer.
 *
 * <p>Key interfaces:
 * <ul>
 *   <li>{@link com.example.order.application.port.in.PlaceOrderUseCase} — Primary use case</li>
 * </ul>
 */
package com.example.order.application.port.in;
