# Task 1: Domain model — Order.allReservationIds + InventoryReservation.withId()

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/domain/Order.java`
- Modify: `order-application/src/main/java/com/example/order/application/domain/InventoryReservation.java`
- Test: `order-application/src/test/java/com/example/order/application/domain/OrderTest.java`

**Interfaces:**
- Consumes: (nothing — new domain field only)
- Produces: `Order.allReservationIds` field + `getAllReservationIds()` getter; `InventoryReservation.withId()` factory

## Tasks

- [ ] **Step 1: Write failing test for `allReservationIds` in OrderTest**

Add to the end of `OrderTest.java`:

```java
// === allReservationIds tests ===

@Test
void testAllReservationIdsStoredAndReturned() {
    List<String> expectedIds = List.of("resv-1", "resv-2");
    Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 3)),
            OrderStatus.CREATED, "idem-1", "resv-1", Instant.now(), expectedIds);
    assertEquals(expectedIds, order.getAllReservationIds());
}

@Test
void testAllReservationIdsIsEmptyListWhenNotProvided() {
    Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
            OrderStatus.CREATED, "idem-1", "resv-1", Instant.now());
    assertTrue(order.getAllReservationIds().isEmpty());
}

@Test
void testAllReservationIdsIsImmutable() {
    List<String> ids = new ArrayList<>(List.of("resv-1"));
    Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
            OrderStatus.CREATED, "idem-1", "resv-1", Instant.now(), ids);
    ids.add("resv-2"); // modify original
    assertEquals(1, order.getAllReservationIds().size());
}

@Test
void testAllReservationIdsRejectsNull() {
    Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
            OrderStatus.CREATED, "idem-1", "resv-1", Instant.now(), null);
    assertTrue(order.getAllReservationIds().isEmpty());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl order-application test -Dtest=OrderTest -DfailIfNoTests=false`
Expected: Compilation fails on `Order` — no 8-param constructor

- [ ] **Step 3: Add `allReservationIds` field to `Order.java`**

Add field, 8-param constructor, delegate 7-param to 8-param:

```java
// New field after createdAt
private final List<String> allReservationIds;

// New 8-param constructor — the canonical one
public Order(String orderId, String customerId, List<OrderItem> items,
             OrderStatus status, String idempotencyKey, String reservationId,
             Instant createdAt, List<String> allReservationIds) {
    this.orderId = Objects.requireNonNull(orderId);
    this.customerId = Objects.requireNonNull(customerId);
    this.items = Collections.unmodifiableList(Objects.requireNonNull(items));
    this.status = Objects.requireNonNull(status);
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    this.reservationId = reservationId;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.allReservationIds = allReservationIds != null
            ? List.copyOf(allReservationIds) : List.of();
}

// Updated 7-param constructor — delegates with empty list
public Order(String orderId, String customerId, List<OrderItem> items,
             OrderStatus status, String idempotencyKey, String reservationId,
             Instant createdAt) {
    this(orderId, customerId, items, status, idempotencyKey, reservationId,
         createdAt, List.of());
}

// New getter
public List<String> getAllReservationIds() {
    return allReservationIds;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl order-application test -Dtest=OrderTest -DfailIfNoTests=false`
Expected: All OrderTest tests pass (including all the existing ones)

- [ ] **Step 5: Add `withId()` factory to `InventoryReservation.java`**

Add after the `pending()` method:

```java
/**
 * Creates a reservation with a specific reservation ID.
 * Used for reconstructing reservations from persisted data (e.g., WMS callback).
 */
public static InventoryReservation withId(String reservationId, String sku,
                                          int quantity, String orderId) {
    return new InventoryReservation(reservationId, sku, quantity, orderId,
            ReservationStatus.PENDING, Instant.now(), null);
}
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(domain): add Order.allReservationIds and InventoryReservation.withId()

- Add 8-param Order constructor with allReservationIds list
- 7-param constructor delegates to 8-param with empty list (backward compat)
- Add InventoryReservation.withId() factory for persisted-data reconstruction
- allReservationIds is immutable and null-safe"
```

## Report Requirements

After completing the task, write a report containing:
- Status: DONE or BLOCKED or NEEDS_CONTEXT
- Commits made (list of commit hashes)
- Test results summary (which tests passed, any failures)
- Any concerns or observations
