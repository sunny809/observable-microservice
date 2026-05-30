package com.example.order.application.port.out;

public interface DomainEventPublisher {
    void publish(Object event);
}
