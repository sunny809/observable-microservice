package com.order.demo.adapter.inbound.rest;

import java.util.List;
import java.util.stream.Collectors;

import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.in.PlaceOrderCommand;

public final class PlaceOrderMapper {

    private PlaceOrderMapper() {}

    public static PlaceOrderCommand toCommand(PlaceOrderRequest req) {
        return new PlaceOrderCommand(
                req.getCustomerId(),
                mapToOrderItems(req.getItems()),
                req.getIdempotencyKey());
    }

    private static List<OrderItem> mapToOrderItems(List<PlaceOrderRequest.OrderItemRequest> items) {
        return items.stream()
                .map(i -> new OrderItem(i.getSku(), i.getQuantity()))
                .collect(Collectors.toList());
    }
}
