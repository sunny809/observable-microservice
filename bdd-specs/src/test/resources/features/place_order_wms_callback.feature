Feature: WMS picking callback
  As a WMS system
  I want to notify the order service when picking is complete
  So that the order saga can proceed to TMS dispatch

  Background:
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction

  Scenario: Callback succeeds when order is in WMS_ACKED
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When the WMS callback is called with the order ID
    Then the callback response status should be 200
    And the order status should eventually be TMS_DISPATCHED

  Scenario: Callback returns 404 for unknown order ID
    When the WMS callback is called with order ID "non-existent-id"
    Then the callback response status should be 404

  Scenario: Callback returns 409 when order is not in WMS_ACKED state
    Given a CREATED order exists in the database
    When the WMS callback is called with the pre-created order ID
    Then the callback response status should be 409
