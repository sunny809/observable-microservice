package com.example.order.application.port.in;

public interface PlaceOrderUseCase {
    OrderPlacedResult placeOrder(PlaceOrderCommand command);
}
