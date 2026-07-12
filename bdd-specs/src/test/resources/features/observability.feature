Feature: Order placement emits observability metrics
  As a DevOps engineer
  I want order placement and saga operations to emit Prometheus metrics
  So that I can monitor business volume, latency, and error rates

  Scenario: successful order emits orders_placed and saga_duration metrics
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client places an order with idempotency key "obs-placed-1"
    Then the actuator prometheus endpoint contains metric "orders_placed_total"
    And the actuator prometheus endpoint contains metric "saga_duration_seconds"
    And the actuator prometheus endpoint contains metric "saga_gap_duration_seconds"
    And the actuator prometheus endpoint contains metric "saga_step_duration_seconds"

  Scenario: duplicate order emits orders_failed metric
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client places an order with idempotency key "obs-dup-1"
    And the client retries the same order with idempotency key "obs-dup-1"
    Then the actuator prometheus endpoint contains metric "orders_failed_total"

  Scenario: successful order emits inventory_reservation metric
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client places an order with idempotency key "obs-inv-1"
    Then the actuator prometheus endpoint contains metric "inventory_reservation_total"
