Feature: TMS dispatch flow

  Background:
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction

  Scenario: TMS accepts dispatch after WMS picking completes
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When WMS picking is completed for the order
    And TMS service accepts dispatch instruction
    Then the order status should eventually be TMS_DISPATCHED
    And the TMS service should receive a dispatch instruction

  Scenario: TMS rejects dispatch after WMS picking completes
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When WMS picking is completed for the order
    And TMS service rejects dispatch instruction
    Then the order status should eventually be TMS_REJECTED
    And the inventory service should eventually release the reservation

  Scenario: TMS service is unavailable after WMS picking completes
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When WMS picking is completed for the order
    And TMS service is unavailable
    Then the order status should eventually be TMS_REJECTED
    And the inventory service should eventually release the reservation
