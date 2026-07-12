# Sprint Report: WMS Callback Endpoint & BDD Fix

**Branch:** `feature/sprint1-o11y-kit-extraction`
**Period:** 2026-06-27
**Status:** Implementation complete, pending push & PR

---

## Summary

Implemented the WMS_PICKED → TMS saga flow by adding a WMS callback REST endpoint and fixing the BDD TMS scenarios from false positives to real end-to-end tests. This completes the missing link in the Saga state machine.

## Commits

```
1f01922 fix: add null check in InventoryConfirmationSchedulerAdapter constructor
214b135 fix: resolve pre-existing test failures
31521d3 fix: address code review findings
e6dd3f1 test(bdd): add WMS callback BDD scenarios + fix TMS flow
efcd4f0 feat(api): add WMS picking callback endpoint
47984dc feat(persistence): add reservation_ids TEXT column for saga data
e19cf67 feat(saga): persist all reservation IDs on order creation
b41e5ce feat(domain): add Order.allReservationIds and InventoryReservation.withId()
763e79e docs: expand BDD section with callback validation, lifecycle scenario, deferred items
cbb772a docs: add WMS callback endpoint + BDD fix design spec
```

## Files Changed

### Domain Layer (`order-application`)
| File | Change |
|------|--------|
| `Order.java` | Added `allReservationIds` field, 8-param constructor, backward-compatible 7-param |
| `InventoryReservation.java` | Added `withId()` factory method |
| `OrderPlacementSaga.java` | Persists all reservation IDs on order creation |

### Adapter Layer (`order-adapter`)
| File | Change |
|------|--------|
| `OrderEntity.java` | Added `reservationIds` TEXT column (JSON array) |
| `OrderPersistenceAdapter.java` | Serialize/deserialize reservation IDs |
| `WmsCallbackController.java` | **New** — `POST /api/orders/wms/callback/picking-completed` |
| `WmsCallbackRequest.java` | **New** — DTO with `@NotBlank orderId` |
| `OrderNotFoundException.java` | **New** — exception for unknown orders |
| `RestExceptionHandler.java` | Added `@ExceptionHandler` for OrderNotFoundException → 404 |

### Infrastructure Layer (`order-infrastructure`)
| File | Change |
|------|--------|
| `V3__add_order_items_column.sql` | Added `reservation_ids TEXT` column |
| `InventoryConfirmationSchedulerAdapter.java` | Added null check in constructor |

### BDD Tests (`bdd-specs`)
| File | Change |
|------|--------|
| `place_order_wms_callback.feature` | **New** — 3 scenarios (200/404/409) |
| `place_order_tms.feature` | Added full saga lifecycle scenario |
| `TmsSteps.java` | Fixed `wmsPickingIsCompletedForTheOrder()` → calls real endpoint |
| `HttpHelper.java` | Added `postWmsPickingCallback()` helper |

### Unit Tests
| File | Change |
|------|--------|
| `OrderTest.java` | 4 new tests for `allReservationIds` |
| `OrderPersistenceAdapterTest.java` | 3 new tests for `reservationIds` serialization |
| `WmsCallbackControllerTest.java` | **New** — 5 tests (200/404/409/multi-item/count mismatch) |
| `OrderPlacementSagaTest.java` | Fixed: replaced `mock(TransactionTemplate)` with real instance |
| `OrderApplicationContextTest.java` | Fixed: excluded ObservedAutoConfiguration bean conflict |

## Pre-existing Issues Resolved

| Issue | Root Cause | Fix |
|-------|-----------|-----|
| `OrderPlacementSagaTest` (10 tests) | Mockito ByteBuddy can't instrument `TransactionTemplate` on JDK 25 | Replaced `mock(TransactionTemplate.class)` with real instance + no-op `AbstractPlatformTransactionManager` |
| `OrderApplicationContextTest` (2 tests) | Bean conflict: o11y-kit and Spring Boot Actuator both register `observedAspect` | Added `ObservedAutoConfiguration.class` to test's `@SpringBootApplication` exclude list |
| `InventoryConfirmationSchedulerAdapterTest` (1 test) | Constructor removed null check, NPE not thrown | Added `Objects.requireNonNull(inventoryPort)` |

## Verification

| Module | Tests | Result |
|--------|-------|--------|
| `order-application` | 79/79 | ✅ PASS |
| `order-adapter` | 99/99 | ✅ PASS (new tests included) |
| `order-infrastructure` | 15/15 | ✅ PASS (ArchitectureTest included) |

## Remaining Pre-existing Issues (JDK 25)

These 4 failures are pre-existing Mockito ByteBuddy incompatibilities on JDK 25, not caused by this branch:

- `TraceAspectTest` (3 tests) — `mock(Tracer.class)`, `mock(Span.class)` fail
- `WmsMessageQueueAdapterTest` (1 test) — `mock(KafkaTemplate.class)` fails

## Architecture Compliance

All ArchUnit rules pass:
- `WmsCallbackController` lives in `..adapter.inbound.rest..` ✅
- Domain layer has no adapter dependencies ✅
- No cyclic dependencies introduced ✅
- `inbound_rest_should_not_depend_on_outbound` ✅

## Follow-up Items (Deferred)

| # | Topic | Description |
|---|-------|-------------|
| 1 | Multi-item + TMS compensation | Extend `place_order_multi_item.feature` to TMS rejection path |
| 2 | Observability BDD | Verify Prometheus metrics/saga.duration in BDD scenarios |
| 3 | Resilience BDD | Circuit breaker state machine, retry, timeout scenarios |