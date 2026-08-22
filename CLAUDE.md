# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Test Standards

Unit tests on this project must meet a strict bar — not just pass:

- Cover **both** the pass/success path **and** the fail/error path of the logic under test (e.g., archive-found deletes *and* archive-not-found skips; snapshot present *and* empty/blank/null/invalid-JSON).
- Use **specific assertions** (`assertEquals`, `assertThat(...).contains(...)`/`doesNotContain(...)`) — not bare `assertTrue(result != null)`, and not `verify(mock)` with `any()` matchers where the actual value matters.
- Design test data from **business scenarios** (realistic SKUs, real order statuses), not generic `foo`/`bar`.
- For security/correctness fixes, assert the **actual transformed output** (e.g., capture the escaped LIKE pattern via `ArgumentCaptor` and assert its exact form) — never mock with `any()` and leave the core logic unexercised.

Coverage is enforced by JaCoCo: 85% LINE coverage by default; `order-adapter` is on an interim 80% floor (see ADR-4). "Green but weak" tests that skip these bullets give false confidence and let real regressions through.

## Known Issues

- **`mvn clean install` fails at `bdd-specs` on Java 25.** The Cucumber test context fails to load with `Unsupported class file major version 69` (Spring's bundled ASM can't parse Java 25 classes). This is a known toolchain issue, **not** a regression from recent edits. While unresolved, get a clean green build of the working modules with:
  ```bash
  mvn -pl order-application,order-adapter,order-infrastructure clean install
  ```
- **`order-o11y` is an empty shell** (no sources, not in the reactor, stale parent). The real observability code (`TracerHelper`, `SpanNames`) is in `order-adapter`'s `observability` package — look there, not in `order-o11y`.

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

- **Order** - Aggregate root with status transitions: `CREATED` → `RESERVED` → `WMS_ACKED` / `REJECTED`
- **InventoryReservation** - Inventory reservation with status (`PENDING`, `CONFIRMED`, `RELEASED`)
- **WmsShipmentInstruction** - Instruction sent to Warehouse Management System

### Key Patterns

1. **Saga Pattern** - `OrderPlacementSaga` orchestrates order creation, inventory reservation, and WMS instruction with compensation logic
2. **Idempotency** - Orders are deduplicated by `idempotencyKey`
3. **Circuit Breakers** - Resilience4j for inventory and WMS service calls
4. **Distributed Tracing** - OpenTelemetry with Jaeger integration

### Dependency Flow

```
order-infrastructure → order-adapter → order-application → order-o11y
```

- `order-application` has no dependencies on other modules
- `order-adapter` depends on `order-application` and `order-o11y`
- `order-infrastructure` depends on `order-adapter` and `order-o11y`

### External Services

- **Inventory Service** (port 8081) - `/api/inventory/reserve`, `/api/inventory/confirm`, `/api/inventory/reserve/{id}`
- **WMS Service** (port 8082) - `/api/wms/shipments`
- **Jaeger** (port 4317) - OTLP endpoint for tracing

## Architecture Decision Records

### ADR-1: TransactionTemplate in Async WMS Callbacks

**Context:** The `OrderPlacementSaga.onWmsRequired()` method is annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, meaning it fires after the initial transaction commits. The WMS callback (`thenAccept`/`exceptionally`) runs on a Reactor Netty thread without an active Spring transaction.

**Decision:** Wrap all database operations in the async callback with `TransactionTemplate.executeWithoutResult()` to ensure they run within a new transaction.

**Consequences:**
- ✅ Saga can update order status and release inventory after WMS responds
- ⚠️ Async callbacks must not rely on the original transaction context
- ⚠️ Requires `TransactionTemplate` to be injected into the saga

### ADR-2: ObjectMapper with Jackson Mixins in Persistence Adapter

**Context:** `OrderItem` is a domain class in `order-application` with no Jackson annotations. The persistence adapter needs to serialize/deserialize it to JSON.

**Decision:** Use a Jackson `@JsonCreator` factory method and a mixin class registered via `ObjectMapper.addMixIn()`.

**Consequences:**
- ✅ Keeps domain classes free of Jackson dependencies
- ⚠️ Requires static `ObjectMapper` configuration (thread-safe after init)
- ⚠️ Serialization failures silently return empty arrays (logged as warnings)

### ADR-3: Span Lifecycle in WmsRestAdapter

**Context:** `WmsRestAdapter.sendInstruction()` creates an OpenTelemetry span, makes an async WebClient call, and returns a `CompletableFuture`.

**Decision:** The span ends in the `finally` block before the async call completes.

**Consequences:**
- ✅ Span covers the synchronous setup phase
- ⚠️ Span duration does not reflect actual HTTP call duration
- ⚠️ HTTP errors are not recorded on the span
- 🔧 Future improvement: Move `span.end()` into `CompletableFuture.whenComplete()`

### ADR-4: JaCoCo Coverage Threshold (85% default, per-module override)

**Context:** Initial threshold was 60%, raised to 75% in `pom.xml`, but the docs claimed 80% — the doc and the build disagreed, and both sat below the team's actual 85%+ standard.

**Decision:** Enforce LINE coverage via the `jacoco.line.minimum` property. Default `0.85` in the root pom; modules that cannot yet meet 85% override the property downward in their own pom. Currently `order-adapter` overrides to `0.80` (its coverage is 83.8%); `order-application` (95.1%) and `order-infrastructure` (87.8%) inherit the 0.85 default. Raise the adapter override toward 0.85 once its coverage is lifted.

**Consequences:**
- ✅ Build-enforced floor matches the documented standard (no more "green at 75% while claiming 80%")
- ✅ New modules inherit the 0.85 default — a laggard must opt out explicitly
- ⚠️ Requires maintaining tests for all new code paths
- ⚠️ Utility classes with private constructors need explicit test coverage
