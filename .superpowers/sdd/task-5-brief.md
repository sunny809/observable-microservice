# Task 5: BDD — add callback feature + steps + fix TMS steps

**Files:**
- Create: `bdd-specs/src/test/resources/features/place_order_wms_callback.feature`
- Modify: `bdd-specs/src/test/java/com/example/order/specs/TmsSteps.java`
- Modify: `bdd-specs/src/test/java/com/example/order/specs/HttpHelper.java`
- Modify: `bdd-specs/src/test/resources/features/place_order_tms.feature`

**Interfaces:**
- Consumes: WmsCallbackController endpoint
- Produces: BDD scenarios verifying callback endpoint + TMS flow + full lifecycle

## Tasks

- [ ] **Step 1: Create `place_order_wms_callback.feature`**

```gherkin
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
```

- [ ] **Step 2: Update `place_order_tms.feature` — add full lifecycle scenario at top**

```gherkin
Feature: TMS dispatch flow

  Background:
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction

  Scenario: Full saga lifecycle — place order through WMS callback to TMS dispatch
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When the WMS callback is called with the order ID
    Then the callback response status should be 200
    And the order status should eventually be TMS_DISPATCHED
    And the TMS service should receive a dispatch instruction

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
```

- [ ] **Step 3: Add `postWmsPickingCallback` to `HttpHelper.java`**

```java
public ResponseEntity<Map> postWmsPickingCallback(String orderId) {
    String body = String.format("{\"orderId\":\"%s\"}", orderId);
    HttpEntity<String> request = new HttpEntity<>(body, defaultHeaders());
    return restTemplate.postForEntity(
            "/api/v1/orders/wms/callback/picking-completed", request, Map.class);
}
```

- [ ] **Step 4: Update `TmsSteps.java`**

Add new field at class level:
```java
private ResponseEntity<Map> callbackResponse;
```

Replace `wmsPickingIsCompletedForTheOrder()` with actual callback call:
```java
@When("WMS picking is completed for the order")
public void wmsPickingIsCompletedForTheOrder() {
    orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
    callbackResponse = httpHelper.postWmsPickingCallback(orderId);
}
```

Add new step definitions:
```java
@When("the WMS callback is called with the order ID")
public void wmsCallbackIsCalledWithOrderId() {
    orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
    callbackResponse = httpHelper.postWmsPickingCallback(orderId);
}

@When("the WMS callback is called with order ID {string}")
public void wmsCallbackIsCalledWithOrderId(String predefinedOrderId) {
    callbackResponse = httpHelper.postWmsPickingCallback(predefinedOrderId);
}

@Then("the callback response status should be {int}")
public void theCallbackResponseStatusShouldBe(int statusCode) {
    assertThat(callbackResponse.getStatusCode().value()).isEqualTo(statusCode);
}

@Given("a CREATED order exists in the database")
public void aCreatedOrderExistsInTheDatabase() {
    jdbcTemplate.execute("INSERT INTO orders (id, customer_id, idempotency_key, " +
            "reservation_id, status, created_at, items, reservation_ids) " +
            "VALUES ('ord-pre-created', 'cust-pre', 'idem-pre', 'resv-pre', " +
            "'CREATED', NOW(), '[]', '[]')");
}

@When("the WMS callback is called with the pre-created order ID")
public void wmsCallbackIsCalledWithPreCreatedOrderId() {
    callbackResponse = httpHelper.postWmsPickingCallback("ord-pre-created");
}
```

- [ ] **Step 5: Run BDD tests**

Run: `mvn -pl bdd-specs test -Dtest=CucumberTestSuite -DfailIfNoTests=false`
Expected: All BDD scenarios pass (20 total)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test(bdd): add WMS callback BDD scenarios + fix TMS flow"
```

## Report Requirements

After completing the task, write a report containing:
- Status: DONE or BLOCKED or NEEDS_CONTEXT
- Commits made (list of commit hashes)
- Test results summary (which tests passed, any failures)
- Any concerns or observations
