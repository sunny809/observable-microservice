package com.order.demo.application.port.out;

import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for the Warehouse Management System (WMS) service.
 *
 * <p>Defines the contract for sending shipment instructions to the WMS.
 * Implementations (such as {@code WmsRestAdapter}) handle the actual HTTP
 * communication and resilience patterns.
 *
 * <p>The {@link #sendInstruction} method is called asynchronously by the saga
 * after the order transaction commits.
 *
 * @see com.order.demo.adapter.outbound.wms.WmsRestAdapter
 * @see com.order.demo.application.service.OrderPlacementSaga#onWmsRequired
 */
public interface WmsPort {

    /**
     * Sends a shipment instruction to the WMS service.
     *
     * @param instruction the shipment instruction containing order and reservation IDs
     * @return a future that completes with the WMS acknowledgment
     */
    CompletableFuture<WmsAck> sendInstruction(WmsShipmentInstruction instruction);

    /**
     * Voids a previously sent shipment instruction, instructing the WMS to
     * abandon the shipment. Used by the cancellation saga when an order is
     * cancelled after the WMS has been instructed.
     *
     * @param instruction the shipment instruction to void (order + reservation IDs)
     * @return a future that completes when the void is acknowledged, or fails if
     *         the WMS cannot be reached
     */
    CompletableFuture<Void> cancelInstruction(WmsShipmentInstruction instruction);
}
