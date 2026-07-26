/**
 * Outbound ports (driven adapters) for the order application.
 *
 * <p>Defines the contracts for external services and repositories
 * that the application layer depends on. Implementations live in
 * the adapter layer (e.g., {@code InventoryRestAdapter}, {@code OrderPersistenceAdapter}).
 *
 * <p>Key interfaces:
 * <ul>
 *   <li>{@link com.order.demo.application.port.out.InventoryPort} — Inventory service</li>
 *   <li>{@link com.order.demo.application.port.out.WmsPort} — WMS service</li>
 *   <li>{@link com.order.demo.application.port.out.OrderRepositoryPort} — Order persistence</li>
 * </ul>
 */
package com.order.demo.application.port.out;
