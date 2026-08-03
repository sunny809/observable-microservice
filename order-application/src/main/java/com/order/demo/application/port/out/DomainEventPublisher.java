package com.order.demo.application.port.out;

public interface DomainEventPublisher {
    void publish(Object event);
}
