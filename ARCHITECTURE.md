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
| `order-o11y` | OpenTelemetry utilities (tracer helper) |
| `bdd-specs` | Cucumber BDD tests with WireMock for service virtualization |

### Domain Model

- **Order** - Aggregate root with status transitions: `CREATED` → `WMS_ACKED` → `WMS_PICKED` → `TMS_DISPATCHED`, with `REJECTED` / `TMS_REJECTED` failure branches
- **InventoryReservation** - Immutable value object with status (`PENDING`, `CONFIRMED`, `RELEASED`)
- **WmsShipmentInstruction / TmsShipmentInstruction** - Event payloads for service integration

### Key Patterns

1. **Saga Pattern** - `OrderPlacementSaga` orchestrates order creation, inventory reservation, WMS instruction, WMS picking, and TMS dispatch with compensating transactions
2. **Idempotency** - Dual-layer deduplication via Caffeine cache (fast path) and database unique constraint (source of truth)
3. **Circuit Breakers** - Resilience4j for all external service calls with per-service thresholds
4. **Distributed Tracing** - OpenTelemetry with W3C traceparent / B3 propagation, exported via OTLP to Jaeger
5. **Async Event Handling** - `@TransactionalEventListener(AFTER_COMMIT)` + `CompletableFuture` + `TransactionTemplate` for reliable async callbacks

### Dependency Flow

```text
order-infrastructure → order-adapter → order-application → order-o11y
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

**Decision:** Extract the observability infrastructure into a separate multi-module SDK (`o11y-kit/`) with its own build lifecycle, versioning, and documentation. The blueprint consumes it as a dependency.

**Consequences:**

- ✅ Clear separation of concerns: blueprint demonstrates usage, SDK is reusable library
- ✅ Independent versioning allows SDK to evolve without affecting the blueprint
- ⚠️ Development requires building both projects (mitigated by Makefile + CI scripts)

### ADR-6: JaCoCo Coverage Threshold at 80%

**Context:** Initial threshold was 60%, which was too low to prevent coverage regression as the codebase grew.

**Decision:** Raise threshold to 80% line coverage per module.

**Consequences:**

- ✅ Prevents coverage regression on new code
- ⚠️ Requires maintaining tests for all new code paths
- ⚠️ Spring Boot auto-configuration classes are excluded from the threshold
