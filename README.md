# Order Service

A production-ready Spring Boot order service demonstrating **hexagonal architecture** (ports and adapters), **Saga pattern**, **circuit breakers**, **idempotency**, and **distributed tracing**.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      order-infrastructure                    │
│              (Spring Boot, OpenTelemetry, JPA)               │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                        order-adapter                           │
│    (REST controllers, HTTP clients, persistence adapters)     │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                      order-application                       │
│         (Domain logic, use cases, ports as interfaces)      │
└─────────────────────────────────────────────────────────────┘
```

## Module Structure

| Module | Purpose |
|--------|---------|
| `order-application` | Core domain logic and use cases (no framework dependencies) |
| `order-adapter` | REST controllers, outbound adapters (HTTP, JPA, Kafka) |
| `order-infrastructure` | Spring Boot application entry point, OpenTelemetry config, persistence |
| `order-o11y` | OpenTelemetry utilities (tracer helper) |
| `bdd-specs` | Cucumber BDD tests with WireMock for service virtualization |

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose (for local services)

### Build

```bash
mvn clean install
```

### Run Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# BDD/Cucumber tests
mvn -pl bdd-specs test -Dtest=CucumberTestSuite

# Architecture tests
mvn -pl order-infrastructure test -Dtest=ArchitectureTest
```

### Run Locally

```bash
# Start dependencies (Jaeger, PostgreSQL)
docker-compose up -d

# Run the application
mvn -pl order-infrastructure spring-boot:run
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Place a new order |
| GET | `/api/orders/{id}` | Get order by ID |

### Place Order Request

```json
{
  "customerId": "customer-1",
  "sku": "sku-1",
  "quantity": 2,
  "idempotencyKey": "idem-key-123"
}
```

### Responses

| Status | Meaning |
|--------|---------|
| 201 | Order created successfully |
| 409 | Duplicate order (same idempotency key) |
| 422 | Insufficient inventory |
| 400 | Validation error |

## Key Patterns

1. **Saga Pattern** - `OrderPlacementSaga` orchestrates order creation, inventory reservation, and WMS instruction with compensation logic
2. **Idempotency** - Orders are deduplicated by `idempotencyKey`
3. **Circuit Breakers** - Resilience4j for inventory and WMS service calls
4. **Distributed Tracing** - OpenTelemetry with OTLP export to Jaeger

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `otel.exporter.endpoint` | `http://localhost:4317` | OpenTelemetry OTLP endpoint |
| `inventory.service.url` | `http://localhost:8081` | Inventory service base URL |
| `wms.service.url` | `http://localhost:8082` | WMS service base URL |

## Contributing

See [CLAUDE.md](CLAUDE.md) for architecture decision records and project conventions.
