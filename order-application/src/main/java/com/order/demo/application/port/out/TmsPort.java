package com.order.demo.application.port.out;

import java.util.concurrent.CompletableFuture;

/**
 * Outbound port for the Transport Management System (TMS) service.
 *
 * <p>Defines the contract for sending dispatch instructions to the TMS.
 * Implementations (such as {@code TmsRestAdapter}) handle the actual HTTP
 * communication and resilience patterns.
 *
 * <p>The {@link #sendInstruction} method is called asynchronously by the saga
 * after the WMS confirms picking is complete.
 *
 * @see com.order.demo.adapter.outbound.tms.TmsRestAdapter
 * @see com.order.demo.application.service.OrderPlacementSaga#onTmsRequired
 */
public interface TmsPort {

    /**
     * Sends a dispatch instruction to the TMS service.
     *
     * @param instruction the dispatch instruction containing order and reservation IDs
     * @return a future that completes with the TMS acknowledgment
     */
    CompletableFuture<TmsAck> sendInstruction(TmsShipmentInstruction instruction);
}
