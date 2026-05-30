Feature: Place order flow

  Scenario: Successfully place order when inventory is available
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client submits a place order request
    Then the response status should be 201
    And the response should contain an orderId and traceId
    And the inventory service should receive a reserve request
    And the WMS service should receive a shipment instruction

  Scenario: Return error when inventory is insufficient
    Given inventory service returns reservation failure
    When the client submits a place order request
    Then the response status should be 422
    And the response should contain an error message
    And the inventory service should receive a reserve request

  Scenario: Return 409 for duplicate order with same idempotency key
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client submits a place order request with the same idempotency key twice
    Then the second response status should be 409
    And the response should contain an error message
