# Task 4: WMS callback controller + DTO + exception

**Files:**
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/WmsCallbackController.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/WmsCallbackRequest.java`
- Create: `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/OrderNotFoundException.java`
- Test: Create `order-adapter/src/test/java/com/example/order/adapter/inbound/rest/WmsCallbackControllerTest.java`

**Interfaces:**
- Consumes: `OrderRepositoryPort`, `DomainEventPublisher`
- Produces: `POST /api/v1/orders/wms/callback/picking-completed` endpoint
- Produces: `OrderNotFoundException` (extends RuntimeException, caught by global handler)

## Tasks

- [ ] **Step 1: Create `OrderNotFoundException.java`**

```java
package com.example.order.adapter.inbound.rest;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
```

- [ ] **Step 2: Create `WmsCallbackRequest.java`**

```java
package com.example.order.adapter.inbound.rest;

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
package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.WmsPickingCompletedEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.OrderRepositoryPort;
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
package com.example.order.adapter.inbound.rest;

import com.example.order.application.domain.InventoryReservation;
import com.example.order.application.domain.Order;
import com.example.order.application.domain.OrderStatus;
import com.example.order.application.domain.WmsPickingCompletedEvent;
import com.example.order.application.port.in.OrderItem;
import com.example.order.application.port.out.DomainEventPublisher;
import com.example.order.application.port.out.OrderRepositoryPort;
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
git commit -m "feat(api): add WMS picking callback endpoint"
```

## Report Requirements

After completing the task, write a report containing:
- Status: DONE or BLOCKED or NEEDS_CONTEXT
- Commits made (list of commit hashes)
- Test results summary (which tests passed, any failures)
- Any concerns or observations
