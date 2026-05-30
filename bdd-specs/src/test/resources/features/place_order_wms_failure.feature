Feature: Place order with WMS failure
  As a client
  I want the order to be properly handled when the WMS service fails
  So that inventory is released and the order is rejected

  Scenario: WMS rejects the shipment instruction
    Given inventory service returns reservation success
    And WMS service rejects shipment instruction
    When the client submits a place order request
    Then the sync response status should be 201
    And the inventory service should eventually release the reservation
    And the order status should eventually be REJECTED

  Scenario: WMS service is unavailable
    Given inventory service returns reservation success
    And WMS service is unavailable
    When the client submits a place order request
    Then the sync response status should be 201
    And the inventory service should eventually release the reservation
    And the order status should eventually be REJECTED