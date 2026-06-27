# Task 4 Report: WMS Callback Controller + DTO + Exception

**Status:** DONE

**Summary:** All four files created — `WmsCallbackController.java`, `WmsCallbackRequest.java`, `OrderNotFoundException.java`, and `WmsCallbackControllerTest.java` — with exact code from the plan spec.

## Details

### Files created
- `order-adapter/src/main/java/.../adapter/inbound/rest/WmsCallbackController.java` — REST controller with `POST /picking-completed`, 404/409/200 handlers, `rebuildReservations()` helper
- `order-adapter/src/main/java/.../adapter/inbound/rest/WmsCallbackRequest.java` — DTO with `@NotBlank orderId`
- `order-adapter/src/main/java/.../adapter/inbound/rest/OrderNotFoundException.java` — extending RuntimeException
- `order-adapter/src/test/java/.../adapter/inbound/rest/WmsCallbackControllerTest.java` — 4 test methods covering 200/404/409/reservation reconstruction

### Commits
None yet — pending commit.

### Test results
Not run — no mvn binary available. Code verified by inspection:
- `WmsCallbackController` imports match the existing project patterns
- Uses `OrderRepositoryPort.findById()` (same pattern as other adapters)
- Uses `DomainEventPublisher.publish()` (same pattern as `OrderPlacementSaga`)
- `WmsCallbackControllerTest` uses `@ExtendWith(MockitoExtension.class)` (standard project pattern)
- All assertions use AssertJ `assertThat` (project standard)
