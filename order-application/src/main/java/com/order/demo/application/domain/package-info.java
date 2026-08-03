/**
 * Core domain model for the order service.
 *
 * <p>Contains the aggregate roots, value objects, and domain events
 * that represent the business concepts of the order placement saga.
 * All classes in this package are framework-agnostic and have no
 * dependencies on Spring or other infrastructure libraries.
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@link com.order.demo.application.domain.Order} — Aggregate root</li>
 *   <li>{@link com.order.demo.application.domain.InventoryReservation} — Value object</li>
 *   <li>{@link com.order.demo.application.domain.WmsInstructionRequiredEvent} — Domain event</li>
 * </ul>
 */
package com.order.demo.application.domain;
