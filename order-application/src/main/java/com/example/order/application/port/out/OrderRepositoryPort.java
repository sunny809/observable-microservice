package com.example.order.application.port.out;

import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import java.util.Optional;

public interface OrderRepositoryPort {
    void save(Order order);

    Optional<Order> findById(String orderId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    void updateStatus(String orderId, OrderStatus status);
}
