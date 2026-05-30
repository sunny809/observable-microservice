# order-adapter

REST controllers, outbound adapters (HTTP, JPA), and inbound ports implementation.

## Key Classes

- `OrderController` - REST API controller
- `InventoryRestAdapter` - Inventory service HTTP client
- `WmsRestAdapter` - WMS service HTTP client
- `OrderPersistenceAdapter` - JPA persistence adapter
- `RestExceptionHandler` - Global exception handler

## Dependencies

- `order-application`
- `order-o11y`
