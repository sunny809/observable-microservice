# Task 2: Saga — persist all reservation IDs on order placement

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java`
- Test: `order-application/src/test/java/com/example/order/application/service/OrderPlacementSagaTest.java`

**Interfaces:**
- Consumes: `Order` 8-param constructor
- Produces: Orders persisted with non-empty `allReservationIds`

## Tasks

- [ ] **Step 1: Update `OrderPlacementSaga.placeOrder()`**

Change the Order creation (around line 128-134):

```java
// Before:
Order order = new Order(orderId,
        command.getCustomerId(),
        command.getItems(),
        OrderStatus.CREATED,
        command.getIdempotencyKey(),
        primaryReservation.getReservationId(),
        Instant.now());

// After:
List<String> allReservationIds = reservations.stream()
        .map(InventoryReservation::getReservationId)
        .toList();

Order order = new Order(orderId,
        command.getCustomerId(),
        command.getItems(),
        OrderStatus.CREATED,
        command.getIdempotencyKey(),
        primaryReservation.getReservationId(),
        Instant.now(),
        allReservationIds);
```

- [ ] **Step 2: Run existing saga tests to verify**

Run: `mvn -pl order-application test -Dtest=OrderPlacementSagaTest -DfailIfNoTests=false`
Expected: All pass

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(saga): persist all reservation IDs on order creation"
```

## Report Requirements

After completing the task, write a report containing:
- Status: DONE or BLOCKED or NEEDS_CONTEXT
- Commits made (list of commit hashes)
- Test results summary (which tests passed, any failures)
- Any concerns or observations
