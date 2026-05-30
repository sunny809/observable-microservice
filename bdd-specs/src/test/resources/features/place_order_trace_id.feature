Feature: Place order with trace ID propagation
  As a client
  I want trace IDs to be propagated through the order flow
  So that I can correlate requests across services

  Scenario: X-B3-TraceId header is used as response trace ID
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client submits a place order request with X-B3-TraceId
    Then the response should contain the same trace ID in X-Trace-Id header

  Scenario: traceparent header is used when X-B3-TraceId is absent
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction
    When the client submits a place order request with traceparent header
    Then the response should contain a trace ID in X-Trace-Id header