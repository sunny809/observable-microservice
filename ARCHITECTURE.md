# Architecture Decision Records

This document records key architectural decisions made during the development of the order service.

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

### ADR-4: JaCoCo Coverage Threshold at 80%

**Context:** Initial threshold was 60%, which was too low to prevent coverage regression.

**Decision:** Raise threshold to 80% line coverage per module.

**Consequences:**
- ✅ Prevents coverage regression on new code
- ⚠️ Requires maintaining tests for all new code paths
- ⚠️ Utility classes with private constructors need explicit test coverage
