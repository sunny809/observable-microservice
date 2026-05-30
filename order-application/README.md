# order-application

Core domain logic and use cases. Contains no framework dependencies.

## Key Classes

- `OrderPlacementSaga` - Saga orchestration for order placement
- `Order` - Domain aggregate root
- `InventoryReservation` - Inventory reservation entity
- `PlaceOrderUseCase` / `PlaceOrderCommand` - Inbound ports
- `InventoryPort` / `WmsPort` / `OrderRepositoryPort` - Outbound ports

## Dependencies

- `order-o11y` (OpenTelemetry utilities)
