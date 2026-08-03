package com.order.demo.application.port.out;

import com.order.demo.application.port.in.OrderDetail;
import com.order.demo.application.port.in.OrderSearchCriteria;
import com.order.demo.application.port.in.OrderSummary;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;

/**
 * Outbound port for order query operations (CQRS read model).
 *
 * <p>Separated from {@link com.order.demo.application.port.out.OrderRepositoryPort}
 * to keep write and read concerns independent. The read model is backed by
 * a database view ({@code order_view}) that denormalizes order, reservation,
 * and saga data for efficient querying.
 */
public interface OrderQueryPort {
    Page<OrderSummary> search(OrderSearchCriteria criteria);
    Optional<OrderDetail> findDetail(String orderId);

    /**
     * Finds the order state at a specific point in time using snapshots.
     *
     * @param orderId     the order ID
     * @param pointInTime the point in time to query
     * @return the order summary at that time, or empty if no snapshot exists
     */
    Optional<OrderSummary> findOrderAt(String orderId, Instant pointInTime);
}
