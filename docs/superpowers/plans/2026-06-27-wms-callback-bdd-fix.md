# WMS Callback Endpoint & BDD Test Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the WMS_PICKED → TMS saga flow by adding a WMS callback REST endpoint and fixing the BDD TMS scenarios from false positives to real end-to-end tests.

**Architecture:** New `WmsCallbackController` in adapter/inbound/rest publishes `WmsPickingCompletedEvent` which the existing saga listener consumes. The `Order` domain aggregate gains `allReservationIds` (persisted as JSON TEXT) to preserve inventory reservation data across the saga. `InventoryReservation` gets `withId()` factory for reconstruction from persisted data.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Data JPA, Cucumber, Jackson, H2 (test)

## Global Constraints

- All new `@RestController` must live in `..adapter.inbound.rest..` per ArchUnit rules
- Domain layer (`order-application`) must NOT depend on adapter or infrastructure code
- `Order` must keep backward-compatible 7-param constructor for existing ~20 tests
- BDD tests must use TestRestTemplate and WireMock for external service mocking
- All tests must pass before commit at each step
- Flyway migration files are in `order-infrastructure/src/main/resources/db/migration/`

---

### Task 1: Domain model — `Order.allReservationIds` + `InventoryReservation.withId()`

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/domain/Order.java`
- Modify: `order-application/src/main/java/com/example/order/application/domain/InventoryReservation.java`
- Test: `order-application/src/test/java/com/example/order/application/domain/OrderTest.java`

**Interfaces:**
- Consumes: (nothing — new domain field only)
- Produces: `Order.allReservationIds` field + `getAllReservationIds()` getter; `InventoryReservation.withId()` factory

- [ ] **Step 1: Write failing test for `allReservationIds` in OrderTest**

Add a new test block at the end of `OrderTest.java`:

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

---

### Task 2: Saga — persist all reservation IDs on order placement

**Files:**
- Modify: `order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java`
- Test: `order-application/src/test/java/com/example/order/application/service/OrderPlacementSagaTest.java`

**Interfaces:**
- Consumes: `Order` 8-param constructor
- Produces: Orders persisted with non-empty `allReservationIds`

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

---

### Task 3: JPA Entity — add reservationIds column

**Files:**
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/OrderEntity.java`
- Modify: `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/OrderPersistenceAdapter.java`
- Modify: `order-infrastructure/src/main/resources/db/migration/V3__add_order_items_column.sql`
- Test: `order-adapter/src/test/java/com/example/order/adapter/outbound/persistence/OrderPersistenceAdapterTest.java`

**Interfaces:**
- Consumes: `Order.getAllReservationIds()` from Task 1
- Produces: Persisted JSON TEXT column with reservation ID array

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

In `toDomain()` — after deserializing items, add:

```java
List<String> allReservationIds = deserializeReservationIds(entity.getReservationIds());
```

And at the domain object construction, add the list as the 8th argument.

Also add the helper methods:

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
git commit -m "feat(persistence): add reservation_ids TEXT column for saga data

- OrderEntity gains reservationIds field (JSON TEXT)
- OrderPersistenceAdapter serializes/deserializes reservation ID list
- Add Flyway migration for the new column"
```

---

### Task 4: WMS callback controller + DTO + exception

**Files:**
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/WmsCallbackController.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/WmsCallbackRequest.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/OrderNotFoundException.java`
- Test: Create `order-adapter/src/test/java/com/example/order/adapter/inbound/rest/WmsCallbackControllerTest.java`

**Interfaces:**
- Consumes: `OrderRepositoryPort`, `DomainEventPublisher`
- Produces: `POST /api/v1/orders/wms/callback/picking-completed` endpoint
- Produces: `OrderNotFoundException` (extends RuntimeException, caught by global handler)

- [ ] **Step 1: Create `OrderNotFoundException.java`**

```java
package com.order.demo.adapter.inbound.rest;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
```

- [ ] **Step 2: Create `WmsCallbackRequest.java`**

```java
package com.order.demo.adapter.inbound.rest;

import jakarta.validation.constraints.NotBlank;

public class WmsCallbackRequest {
    @NotBlank
    private String orderId;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
```

- [ ] **Step 3: Create `WmsCallbackController.java`**

```java
package com.order.demo.adapter.inbound.rest;

import com.order.demo.application.domain.InventoryReservation;
import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.domain.WmsPickingCompletedEvent;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.out.DomainEventPublisher;
import com.order.demo.application.port.out.OrderRepositoryPort;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/wms/callback")
public class WmsCallbackController {

    private final OrderRepositoryPort orderRepository;
    private final DomainEventPublisher eventPublisher;

    public WmsCallbackController(OrderRepositoryPort orderRepository,
                                  DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/picking-completed")
    public ResponseEntity<Map<String, String>> onPickingCompleted(
            @Valid @RequestBody WmsCallbackRequest request) {

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        if (order.getStatus() != OrderStatus.WMS_ACKED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "invalid_order_status",
                    "current", order.getStatus().name(),
                    "expected", OrderStatus.WMS_ACKED.name()
            ));
        }

        List<InventoryReservation> reservations = rebuildReservations(order);

        eventPublisher.publish(new WmsPickingCompletedEvent(
                order.getOrderId(),
                order.getReservationId(),
                reservations));

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    private List<InventoryReservation> rebuildReservations(Order order) {
        List<OrderItem> items = order.getItems();
        List<String> reservationIds = order.getAllReservationIds();
        List<InventoryReservation> reservations = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            String rid = i < reservationIds.size()
                    ? reservationIds.get(i)
                    : UUID.randomUUID().toString();
            reservations.add(InventoryReservation.withId(
                    rid, item.getSku(), item.getQuantity(), order.getOrderId()));
        }
        return reservations;
    }
}
```

- [ ] **Step 4: Create `WmsCallbackControllerTest.java`**

```java
package com.order.demo.adapter.inbound.rest;

import com.order.demo.application.domain.InventoryReservation;
import com.order.demo.application.domain.Order;
import com.order.demo.application.domain.OrderStatus;
import com.order.demo.application.domain.WmsPickingCompletedEvent;
import com.order.demo.application.port.in.OrderItem;
import com.order.demo.application.port.out.DomainEventPublisher;
import com.order.demo.application.port.out.OrderRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WmsCallbackControllerTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private DomainEventPublisher eventPublisher;

    private WmsCallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new WmsCallbackController(orderRepository, eventPublisher);
    }

    @Test
    void shouldReturn200AndPublishEventWhenOrderIsWmsAcked() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.WMS_ACKED, "idem-1", "resv-1", Instant.now(),
                List.of("resv-1"));
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        ResponseEntity<Map<String, String>> response = controller.onPickingCompleted(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "accepted");

        ArgumentCaptor<WmsPickingCompletedEvent> captor =
                ArgumentCaptor.forClass(WmsPickingCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("ord-1");
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        when(orderRepository.findById("non-existent")).thenReturn(Optional.empty());

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("non-existent");

        assertThatThrownBy(() -> controller.onPickingCompleted(request))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("non-existent");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReturn409WhenOrderIsNotWmsAcked() {
        Order order = new Order("ord-1", "cust-1", List.of(new OrderItem("SKU-1", 2)),
                OrderStatus.CREATED, "idem-1", "resv-1", Instant.now());
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        ResponseEntity<Map<String, String>> response = controller.onPickingCompleted(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "invalid_order_status");
        assertThat(response.getBody()).containsEntry("current", "CREATED");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldRebuildReservationsWithAllReservationIds() {
        Order order = new Order("ord-1", "cust-1",
                List.of(new OrderItem("SKU-1", 2), new OrderItem("SKU-2", 3)),
                OrderStatus.WMS_ACKED, "idem-1", "resv-1", Instant.now(),
                List.of("resv-1", "resv-2"));
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));

        WmsCallbackRequest request = new WmsCallbackRequest();
        request.setOrderId("ord-1");

        controller.onPickingCompleted(request);

        ArgumentCaptor<WmsPickingCompletedEvent> captor =
                ArgumentCaptor.forClass(WmsPickingCompletedEvent.class);
        verify(eventPublisher).publish(captor.capture());

        List<InventoryReservation> reservations = captor.getValue().getReservations();
        assertThat(reservations).hasSize(2);
        assertThat(reservations.get(0).getReservationId()).isEqualTo("resv-1");
        assertThat(reservations.get(1).getReservationId()).isEqualTo("resv-2");
        assertThat(reservations.get(0).getSku()).isEqualTo("SKU-1");
        assertThat(reservations.get(1).getSku()).isEqualTo("SKU-2");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -pl order-adapter test -Dtest=WmsCallbackControllerTest -DfailIfNoTests=false`
Expected: All pass

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(api): add WMS picking callback endpoint

- POST /api/v1/orders/wms/callback/picking-completed endpoint
- OrderNotFoundException for unknown orders (404)
- WmsCallbackRequest DTO with @NotBlank validation
- 409 when order status is not WMS_ACKED (state machine guard)
- rebuildReservations() uses persisted allReservationIds"
```

---

### Task 5: BDD — add callback feature + steps + fix TMS steps

**Files:**
- Create: `bdd-specs/src/test/resources/features/place_order_wms_callback.feature`
- Modify: `bdd-specs/src/test/java/com/example/order/specs/TmsSteps.java`
- Modify: `bdd-specs/src/test/java/com/example/order/specs/HttpHelper.java`
- Modify: `bdd-specs/src/test/resources/features/place_order_tms.feature`

**Interfaces:**
- Consumes: WmsCallbackController endpoint
- Produces: BDD scenarios verifying callback endpoint + TMS flow + full lifecycle

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

Add new fields at class level:
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
```

For the "pre-created order" step in `place_order_wms_callback.feature` (409 scenario), add:
```java
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

Add imports needed:
```java
import org.springframework.jdbc.core.JdbcTemplate;
```

And either inject `@Autowired JdbcTemplate jdbcTemplate;` (it's already present in TmsSteps).

- [ ] **Step 5: Wire `WmsCallbackController` bean into the test context**

The BDD test uses `TestRestTemplate` which starts the full Spring context. The `WmsCallbackController` depends on `OrderRepositoryPort` (already has an implementation: `OrderPersistenceAdapter`) and `DomainEventPublisher` (implementation: `SpringDomainEventPublisher`). Both are already scanned. No additional config needed. The controller will be automatically picked up by component scanning.

- [ ] **Step 6: Run BDD tests**

Run: `mvn -pl bdd-specs test -Dtest=CucumberTestSuite -DfailIfNoTests=false`
Expected: All 5 new scenarios + 15 existing scenarios pass (20 total)

> Note: If this is the first run with the Flyway migration, the reservation_ids column might not exist in the test H2 DB. The test application.yml uses `ddl-auto: create-drop` (not Flyway), so we need Hibernate to create the column automatically. Since we added the `@Column(name = "reservation_ids")` annotation to `OrderEntity`, Hibernate should pick it up. But we may need to verify.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "test(bdd): add WMS callback BDD scenarios + fix TMS flow

- New place_order_wms_callback.feature (3 scenarios)
- Full saga lifecycle scenario in place_order_tms.feature
- TMS steps now actually triggers callback endpoint (fixes false positives)
- HttpHelper.postWmsPickingCallback() helper method"
```

---

### Self-Review

**Spec coverage check:**
1. ALL files from the spec's file change table are covered across Tasks 1-5 ✓
2. BDD scenarios: 20 total as specified in the verification matrix ✓
3. Error handling (404/409) covered in controller test and BDD ✓
4. Data flow from spec's sequence diagram covered across tasks ✓
5. `InventoryReservation.withId()` covers the factory method requirement ✓

**Placeholder scan:** All steps contain complete code. No TBD, TODO, or `// implement later`. ✓

**Type consistency:** 
- `Order` 8-param constructor signature is consistent across Tasks 1, 3, 4 ✓
- `InventoryReservation.withId(String, String, int, String)` used consistently in Task 4 ✓
- `List<String> getAllReservationIds()` used consistently across Tasks 1, 3, 4 ✓
