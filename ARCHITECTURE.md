# Architecture Decision Records

This document records key architectural decisions made during the development of this microservice blueprint.

## Build and Test Commands

```bash
# Build the project
mvn clean install

# Run unit tests
mvn test

# Run integration tests
mvn verify

# Run BDD/Cucumber tests
mvn -pl bdd-specs test -Dtest=CucumberTestSuite

# Run a single test class
mvn -pl order-application test -Dtest=OrderPlacementSagaTest

# Run ArchUnit architecture tests
mvn -pl order-infrastructure test -Dtest=ArchitectureTest

# Start services with Docker Compose
docker-compose up -d
```

## Project Architecture

This is a **hexagonal architecture** (ports and adapters) Spring Boot order service with the following module structure:

### Module Structure

| Module | Purpose |
|--------|---------|
| `order-application` | Core domain logic and use cases (no framework dependencies) |
| `order-adapter` | REST controllers, outbound adapters (HTTP, JPA, Kafka), inbound ports implementation |
| `order-infrastructure` | Spring Boot application entry point, OpenTelemetry config, persistence |
| `(removed, merged into order-adapter)` | OpenTelemetry utilities (tracer helper) |
| `bdd-specs` | Cucumber BDD tests with WireMock for service virtualization |

### Domain Model

- **Order** - Aggregate root with status transitions: `CREATED` → `WMS_ACKED` → `WMS_PICKED` → `TMS_DISPATCHED`, failure branches `REJECTED` / `TMS_REJECTED`, and a `CANCELLED` terminal state reachable from any pre-dispatch state (`CREATED`/`RESERVED`/`WMS_ACKED`/`WMS_PICKED`)
- **InventoryReservation** - Immutable value object with status (`PENDING`, `CONFIRMED`, `RELEASED`)
- **WmsShipmentInstruction / TmsShipmentInstruction** - Event payloads for service integration

### Key Patterns

1. **Saga Pattern** - `OrderPlacementSaga` orchestrates order creation, inventory reservation, WMS instruction, WMS picking, and TMS dispatch with compensating transactions; `OrderCancellationSaga` runs the reverse flow (release inventory, void WMS) to cancel an order before dispatch
2. **Idempotency** - Dual-layer deduplication via Caffeine cache (fast path) and database unique constraint (source of truth)
3. **Circuit Breakers** - Resilience4j for all external service calls with per-service thresholds
4. **Distributed Tracing** - OpenTelemetry with W3C traceparent / B3 propagation, exported via OTLP to Jaeger
5. **Async Event Handling** - `@TransactionalEventListener(AFTER_COMMIT)` + `CompletableFuture` + `TransactionTemplate` for reliable async callbacks

### Dependency Flow

```text
order-infrastructure → order-adapter → order-application → (removed, merged into order-adapter)
                                     ↘                    ↗
                                    o11y-kit (extracted SDK)
```

- `order-application` has no dependencies on other modules (pure Java)
- `order-adapter` depends on `order-application` and the o11y-kit starter
- `order-infrastructure` depends on `order-adapter`

### External Services

| Service | Port | Endpoints |
|---------|------|-----------|
| Inventory Service | 8081 | `/api/inventory/reserve`, `/api/inventory/confirm`, `/api/inventory/reserve/{id}` |
| WMS Service | 8082 | `/api/wms/shipments` |
| TMS Service | 8083 | `/api/tms/dispatches` |
| Jaeger | 4317 | OTLP gRPC endpoint |

---

## ADR Index

### ADR-1: TransactionTemplate in Async WMS/TMS Callbacks

**Context:** The saga methods annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` fire after the initial transaction commits. The CompletableFuture callbacks (`thenCompose`/`exceptionally`) run on Reactor Netty threads without an active Spring transaction.

**Decision:** Wrap all database operations in async callbacks with `TransactionTemplate.executeWithoutResult()` to ensure they run within a new transaction.

**Consequences:**

- ✅ Saga can update order status and release inventory after WMS/TMS responds
- ⚠️ Async callbacks must not rely on the original transaction context
- ⚠️ Requires `TransactionTemplate` to be injected into the saga

### ADR-2: Jackson Mixins for Domain Objects

**Context:** Domain classes in `order-application` have no Jackson annotations to keep them framework-agnostic. The persistence adapter needs to serialize/deserialize them to JSON.

**Decision:** Use Jackson `@JsonCreator` factory methods and mixin classes registered via `ObjectMapper.addMixIn()`.

**Consequences:**

- ✅ Keeps domain classes free of Jackson dependencies
- ⚠️ Requires static `ObjectMapper` configuration (thread-safe after init)
- ⚠️ Serialization failures silently return empty arrays (logged as warnings)

### ADR-3: Span Lifecycle in Async HTTP Adapters

**Context:** `InventoryRestAdapter` creates an OpenTelemetry span for each HTTP call. The old synchronous code ended the span before the HTTP call completed. The new async code uses the reactive pipeline.

**Decision:** Span lifecycle is managed via `doFinally(sig -> span.end())` in the WebClient reactive chain. The span is started synchronously and ended when the Mono completes (success, error, or cancel).

**Consequences:**

- ✅ Span duration now accurately reflects HTTP call duration
- ✅ Errors and cancellations are recorded on the span
- ⚠️ The OTel `Scope` (via `span.makeCurrent()`) closes before the HTTP response arrives, since the method returns immediately with a `CompletableFuture`
- ⚠️ Reactor context propagation is a known limitation — callbacks run outside the original scope

### ADR-4: Dual-Layer Idempotency

**Context:** Order placement requests can arrive multiple times due to network retries or client retry logic. Duplicate detection must be both fast (sub-millisecond) and durable across restarts.

**Decision:** Implement a two-tier idempotency check:

1. **Caffeine cache** (fast path): 30-minute TTL, max 10,000 entries, sub-millisecond lookup
2. **Database unique constraint** (source of truth): `findByIdempotencyKey()` query on cache miss

**Consequences:**

- ✅ Sub-millisecond duplicate detection for hot keys
- ✅ Survives service restarts via database persistence
- ⚠️ Cache and database can temporarily diverge (cache TTL trades consistency for performance)

### ADR-5: o11y-kit Extraction as Separate SDK

**Context:** The observability interceptor code (metrics recording, trace ID resolution, span management) was initially inline in the order-adapter module. As the patterns matured, they became reusable across projects.

**Decision:** Extract the observability infrastructure into a separate multi-module SDK (`(external dependency)`) with its own build lifecycle, versioning, and documentation. The blueprint consumes it as a dependency.

**Consequences:**

- ✅ Clear separation of concerns: blueprint demonstrates usage, SDK is reusable library
- ✅ Independent versioning allows SDK to evolve without affecting the blueprint
- ⚠️ Development requires building both projects (mitigated by Makefile + CI scripts)

### ADR-6: JaCoCo Coverage Threshold (85% default, per-module override)

**Context:** Initial threshold was 60%, raised to 75% in `pom.xml`, but the docs claimed 80% — the doc and the build disagreed, and both sat below the team's actual 85%+ standard.

**Decision:** Enforce LINE coverage via the `jacoco.line.minimum` property. Default `0.85` in the root pom; modules that cannot yet meet 85% override the property downward in their own pom. Currently `order-adapter` overrides to `0.80` (its coverage is 83.8%); `order-application` (95.1%) and `order-infrastructure` (87.8%) inherit the 0.85 default.

**Consequences:**

- ✅ Build-enforced floor matches the documented standard
- ✅ New modules inherit the 0.85 default — a laggard must opt out explicitly
- ⚠️ Requires maintaining tests for all new code paths
- ⚠️ Spring Boot auto-configuration classes are excluded from the threshold

### ADR-7: Order Cancellation Saga

**Context:** The blueprint only demonstrated the forward order-placement flow. A complete saga story also needs the reverse flow — cancelling an order and undoing its side effects (inventory reservation, WMS instruction) safely and idempotently.

**Decision:** Implement `OrderCancellationSaga` as a synchronous, `@Transactional` use case that compensates the placement saga:

1. Release every reserved inventory item, with per-reservation idempotency via the compensation log.
2. Void the WMS instruction if the order reached `WMS_ACKED`/`WMS_PICKED` (best-effort; a void failure does not block cancellation).
3. Transition the order to a new `CANCELLED` terminal state, guarded by the `ALLOWED_TRANSITIONS` map so only pre-dispatch states may be cancelled.

Cancellation is idempotent (a second cancel of an already-cancelled order is a no-op) and requires a `customerId` for ownership validation.

**Consequences:**

- ✅ Demonstrates saga compensation in the reverse direction, closing the "how do I undo a multi-step async process" gap
- ✅ Reuses existing compensation infrastructure (compensation log, saga log, snapshots) rather than introducing new mechanisms
- ⚠️ WMS void is best-effort — inventory release remains the authoritative compensation
- ⚠️ Orders already `TMS_DISPATCHED` cannot be cancelled; that is a separate "recall" flow, out of scope
