# Task 3: JPA Entity — add reservationIds column

**Files:**
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/OrderEntity.java`
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/OrderPersistenceAdapter.java`
- Modify: `order-infrastructure/src/main/resources/db/migration/V3__add_order_items_column.sql`
- Test: `order-adapter/src/test/java/com/example/order/adapter/outbound/persistence/OrderPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `Order.getAllReservationIds()` from Task 1
- Produces: Persisted JSON TEXT column with reservation ID array

## Tasks

- [ ] **Step 1: Write failing tests for reservationIds serialization in `OrderPersistenceAdapterTest.java`**

Add after `testUpdateStatusDelegatesToJpqlUpdate()`:

```java
@Test
void testSaveSerializesReservationIds() {
    List<String> resvIds = List.of("resv-abc", "resv-def");
    Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
            OrderStatus.CREATED, "idem-1", "resv-abc", java.time.Instant.now(), resvIds);

    adapter.save(order);

    verify(repository).save(argThat(entity ->
            entity.getReservationIds() != null &&
            entity.getReservationIds().contains("resv-abc") &&
            entity.getReservationIds().contains("resv-def")));
}

@Test
void testFindByIdDeserializesReservationIds() {
    OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-abc",
            "CREATED", LocalDateTime.now());
    entity.setItems("[]");
    entity.setReservationIds("[\"resv-abc\",\"resv-def\"]");
    when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

    Optional<Order> result = adapter.findById("ord-1");

    assertTrue(result.isPresent());
    assertEquals(List.of("resv-abc", "resv-def"), result.get().getAllReservationIds());
}

@Test
void testFindByIdReturnsEmptyReservationIdsWhenNull() {
    OrderEntity entity = new OrderEntity("ord-1", "cust-1", "idem-1", "resv-abc",
            "CREATED", LocalDateTime.now());
    entity.setItems("[]");
    entity.setReservationIds(null);
    when(repository.findById("ord-1")).thenReturn(Optional.of(entity));

    Optional<Order> result = adapter.findById("ord-1");

    assertTrue(result.isPresent());
    assertTrue(result.get().getAllReservationIds().isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl order-adapter test -Dtest=OrderPersistenceAdapterTest -DfailIfNoTests=false`
Expected: Compilation fails on `OrderEntity` — missing `reservationIds` field

- [ ] **Step 3: Add `reservationIds` field to `OrderEntity.java`**

```java
// After the items field
@Lob
@Column(name = "reservation_ids", columnDefinition = "TEXT")
private String reservationIds;

// Getter and setter
public String getReservationIds() { return reservationIds; }
public void setReservationIds(String reservationIds) { this.reservationIds = reservationIds; }
```

- [ ] **Step 4: Update `OrderPersistenceAdapter.java`**

In `toEntity()` — after setting items JSON, add:
```java
String reservationIdsJson = serializeReservationIds(order.getAllReservationIds());
entity.setReservationIds(reservationIdsJson);
```

In `toDomain()` — after deserializing items, build allReservationIds and pass to constructor.

The full updated `toDomain()` method:
```java
private Order toDomain(OrderEntity entity) {
    List<OrderItem> items = deserializeItems(entity.getItems());
    List<String> allReservationIds = deserializeReservationIds(entity.getReservationIds());
    return new Order(
            entity.getId(),
            entity.getCustomerId(),
            items,
            OrderStatus.valueOf(entity.getStatus()),
            entity.getIdempotencyKey(),
            entity.getReservationId(),
            entity.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant(),
            allReservationIds);
}
```

The full updated `toEntity()` method:
```java
private OrderEntity toEntity(Order order) {
    String itemsJson = serializeItems(order.getItems());
    String reservationIdsJson = serializeReservationIds(order.getAllReservationIds());
    OrderEntity entity = new OrderEntity(
            order.getOrderId(),
            order.getCustomerId(),
            order.getIdempotencyKey(),
            order.getReservationId(),
            order.getStatus().name(),
            LocalDateTime.ofInstant(order.getCreatedAt(), java.time.ZoneOffset.UTC));
    entity.setItems(itemsJson);
    entity.setReservationIds(reservationIdsJson);
    return entity;
}
```

Add the helper methods:
```java
private String serializeReservationIds(List<String> reservationIds) {
    try {
        if (reservationIds == null || reservationIds.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(reservationIds);
    } catch (Exception e) {
        throw new IllegalStateException("Failed to serialize reservation IDs", e);
    }
}

private List<String> deserializeReservationIds(String reservationIdsJson) {
    if (reservationIdsJson == null || reservationIdsJson.isBlank()) {
        return Collections.emptyList();
    }
    try {
        return objectMapper.readValue(reservationIdsJson,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
    } catch (Exception e) {
        log.error("Failed to deserialize reservation IDs from JSON: {}", reservationIdsJson, e);
        throw new IllegalStateException("Failed to deserialize reservation IDs", e);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl order-adapter test -Dtest=OrderPersistenceAdapterTest -DfailIfNoTests=false`
Expected: All tests pass

- [ ] **Step 6: Update Flyway migration**

Append to `V3__add_order_items_column.sql`:
```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS reservation_ids TEXT;
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(persistence): add reservation_ids TEXT column for saga data"
```

## Report Requirements

After completing the task, write a report containing:
- Status: DONE or BLOCKED or NEEDS_CONTEXT
- Commits made (list of commit hashes)
- Test results summary (which tests passed, any failures)
- Any concerns or observations
