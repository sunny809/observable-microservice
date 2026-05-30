Feature: Place order with inventory service circuit breaker
  As a client
  I want a clear error when the inventory service is down
  So that I know the failure is due to infrastructure, not business logic

  Scenario: Inventory service unavailable returns 500
    Given inventory service is unavailable
    When the client submits a place order request
    Then the response status should be 500