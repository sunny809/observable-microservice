Feature: Place order validation
  As a client
  I want to receive clear validation errors for invalid requests
  So that I can correct my request

  Scenario: Reject order with missing customerId
    When the client submits an order request with missing customerId
    Then the response status should be 400

  Scenario: Reject order with blank idempotencyKey
    When the client submits an order request with blank idempotencyKey
    Then the response status should be 400

  Scenario: Reject order with empty items list
    When the client submits an order request with empty items list
    Then the response status should be 400
