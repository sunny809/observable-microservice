package com.example.order.application.port.out;

import com.example.order.application.domain.InventoryReservation;
import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for inventory service operations.
 *
 * <p>Defines the contract for reserving, confirming, and releasing inventory
 * through the inventory service. Implementations (such as {@code InventoryRestAdapter})
 * handle the actual HTTP communication and resilience patterns.
 *
 * <p>All operations return {@link CompletableFuture} for asynchronous composition
 * within the {@link com.example.order.application.service.OrderPlacementSaga}.
 *
 * @see com.example.order.adapter.outbound.inventory.InventoryRestAdapter
 */
public interface InventoryPort {

    /**
     * Attempts to reserve inventory for a single order item.
     *
     * @param request the reservation request containing SKU, quantity, and order ID
     * @return a future that completes with the reservation details, or {@code null}
     *         if the inventory service reports insufficient stock
     */
    CompletableFuture<InventoryReservation> occupy(ReservationRequest request);

    /**
     * Confirms a previously reserved inventory item.
     *
     * @param request the confirmation command containing the reservation ID
     * @return a future that completes when the confirmation is acknowledged
     */
    CompletableFuture<Void> confirm(ConfirmReservationCommand request);

    /**
     * Releases a previously reserved inventory item, returning stock to the pool.
     *
     * @param reservationId the ID of the reservation to release
     * @return a future that completes when the release is acknowledged
     */
    CompletableFuture<Void> release(String reservationId);
}
