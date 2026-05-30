Feature: Place order with multiple items
  As a client
  I want to order multiple SKUs in a single request
  So that I can reserve all items at once

  Scenario: All items reserved successfully
    Given inventory service returns reservation success for all items
    And WMS service accepts shipment instruction
    When the client submits a place order request with multiple items
    Then the response status should be 201
    And the inventory service should receive 2 reserve requests
    And the WMS service should receive a shipment instruction

  Scenario: Second item fails reservation, first item is released
    Given inventory service returns success for first item and failure for second item
    When the client submits a place order request with multiple items
    Then the response status should be 422
    And the inventory service should receive a release request