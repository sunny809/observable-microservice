# WMS 拣货完成回调端点 & BDD 测试正确性修复

## 概述

修复 `place_order_tms.feature` 中 TMS 派送场景的假阳性问题。根本原因是 `WmsPickingCompletedEvent` 在生产代码中无发布者，导致 WMS_PICKED → TMS 的 Saga 链路不可达。

通过新增 WMS 回调 REST 端点 `POST /api/v1/orders/wms/callback/picking-completed`，补全 Saga 链路，使 BDD 场景可真实触发完整流程。

## 问题分析

### 当前状态

```
placeOrder() → WmsInstructionRequiredEvent → onWmsRequired() → WMS API 调用
  → WMS_ACKED (链路中断)
  → onWmsPickingCompleted() 永远不可达 → TMS 相关所有代码均为死代码
```

`TmsSteps.java` 中的 `wmsPickingIsCompletedForTheOrder()` 仅有注释，无实际触发逻辑，导致 3 个 TMS BDD 场景为假阳性。

### 根因

- `WmsPickingCompletedEvent` 领域事件已定义，消费方 `onWmsPickingCompleted()` 已实现
- 但**没有任何代码发布该事件**
- 真实的 WMS 回调 API 尚未实现

## 设计决策

### 决策 1: 回调端点入参 — 只传 `orderId`

WMS 回调只需携带 `orderId`，服务端通过 `OrderRepositoryPort.findById()` 获取 Order 聚合，从中提取 `allReservationIds`、`items`、`reservationId` 等信息，重建完整的 `List<InventoryReservation>`。

### 决策 2: 端点路径

```
POST /api/v1/orders/wms/callback/picking-completed
```

- 新控制器 `WmsCallbackController`，放在 `..adapter.inbound.rest..`
- 不要求 `@PreAuthorize`（内部服务间调用，非用户 API）

### 决策 3: 持久化 `allReservationIds`

Order 聚合新增 `List<String> allReservationIds`，存储所有 inventory 预占返回的真实 reservationId，用于：

1. **回调时重建** `WmsPickingCompletedEvent` 所需的 `List<InventoryReservation>`
2. **TMS 补偿时** `releaseAllAsync()` 需要正确的 reservationId 释放预占

存储方式：`OrderEntity` 新增 `reservationIds TEXT` 列，JSON 数组序列化。

## 组件改动

### 1. Domain — `Order.java`

```java
// 新增字段
private final List<String> allReservationIds;

// 保留 7 参数构造器（测试向后兼容），默认 allReservationIds = List.of()
public Order(String orderId, String customerId, List<OrderItem> items,
             OrderStatus status, String idempotencyKey, String reservationId,
             Instant createdAt) {
    this(orderId, customerId, items, status, idempotencyKey, reservationId,
         createdAt, List.of());
}

// 新增 8 参数构造器，含 allReservationIds
public Order(String orderId, String customerId, List<OrderItem> items,
             OrderStatus status, String idempotencyKey, String reservationId,
             Instant createdAt, List<String> allReservationIds) {
    // ... 原初始化逻辑 ...
    this.allReservationIds = allReservationIds != null
        ? List.copyOf(allReservationIds) : List.of();
}

public List<String> getAllReservationIds() { return allReservationIds; }
```

> 设计意图：保留 7 参构造器确保现有 ~20 个测试调用点无需修改，Saga 和持久化使用 8 参构造器。

#### `InventoryReservation` — 新增工厂方法

```java
// 新增 — 用于 WMS 回调时用真实 reservationId 重建对象
public static InventoryReservation withId(String reservationId, String sku,
                                          int quantity, String orderId) {
    return new InventoryReservation(reservationId, sku, quantity, orderId,
        ReservationStatus.PENDING, Instant.now(), null);
}
```

> `pending()` 保留不变（生成随机 UUID），`withId()` 使用调用方提供的真实 ID。

### 2. Domain — `OrderPlacementSaga.java`

```java
// placeOrder() 中创建 Order 时传入所有 reservationId
List<String> allReservationIds = reservations.stream()
    .map(InventoryReservation::getReservationId)
    .toList();

Order order = new Order(orderId, command.getCustomerId(), command.getItems(),
    OrderStatus.CREATED, command.getIdempotencyKey(),
    primaryReservation.getReservationId(), Instant.now(),
    allReservationIds);  // ← 新增
```

### 3. JPA — `OrderEntity.java`

```java
@Lob
@Column(name = "reservation_ids", columnDefinition = "TEXT")
private String reservationIds;  // JSON array: ["resv-1","resv-2"]
```

### 4. Adapter — `OrderPersistenceAdapter.java`

```java
// toEntity() — 序列化
String reservationIdsJson = objectMapper.writeValueAsString(order.getAllReservationIds());
entity.setReservationIds(reservationIdsJson);

// toDomain() — 反序列化
List<String> allReservationIds = deserializeReservationIds(entity.getReservationIds());
```

### 5. 新增 Controller — `WmsCallbackController.java`

**包路径:** `com.example.order.adapter.inbound.rest`

```java
@RestController
@RequestMapping("/api/v1/orders/wms/callback")
public class WmsCallbackController {

    private final OrderRepositoryPort orderRepository;
    private final DomainEventPublisher eventPublisher;

    @PostMapping("/picking-completed")
    public ResponseEntity<Map<String, String>> onPickingCompleted(
            @Valid @RequestBody WmsCallbackRequest request) {

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(request.getOrderId()));

        // 状态机校验：只有 WMS_ACKED 才能推进到 WMS_PICKED
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

### 6. 新增 DTO — `WmsCallbackRequest.java`

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

### 7. 新增异常 — `OrderNotFoundException.java`

```java
package com.example.order.adapter.inbound.rest;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
    }
}
```

### 8. 数据迁移 — `V3__add_order_items_column.sql`

追加:
```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS reservation_ids TEXT;
```

### 9. BDD 修复

#### `HttpHelper.java` — 新增方法

```java
public ResponseEntity<Map> postWmsPickingCallback(String orderId) {
    String body = String.format("{\"orderId\":\"%s\"}", orderId);
    HttpEntity<String> request = new HttpEntity<>(body, defaultHeaders());
    return restTemplate.postForEntity(
        "/api/v1/orders/wms/callback/picking-completed", request, Map.class);
}
```

#### `TmsSteps.java` — 修复步骤

```java
@When("WMS picking is completed for the order")
public void wmsPickingIsCompletedForTheOrder() {
    orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
    httpHelper.postWmsPickingCallback(orderId);
}
```

## 数据流

```
BDD Test / WMS                     Controller                      Saga                          TMS Mock
   |                                    |                            |                              |
   | POST /api/v1/orders/wms/callback/  |                            |                              |
   |   picking-completed                |                            |                              |
   | orderId: "ord-xxx"                |                            |                              |
   |──────────────────────────────────>|                            |                              |
   |                                    | findById("ord-xxx")       |                              |
   |                                    |──────────────────────────>|                              |
   |                                    | Order + items +           |                              |
   |                                    |   allReservationIds       |                              |
   |                                    |<──────────────────────────|                              |
   |                                    |                            |                              |
   |                                    | publish WmsPicking        |                              |
   |                                    |   CompletedEvent          |                              |
   |                                    |───────────────────────────>|                              |
   |                                    |                            | updateStatus → WMS_PICKED    |
   |                                    |                            | sagaLogPort.recordStep       |
   |                                    |                            | publish TmsInstructionReq    |
   |                                    |                            |─────────────────────────────>|
   |                                    |                            | POST /api/tms/dispatches     |
   |                                    |                            |<─────────────────────────────|
   |                                    |                            | updateStatus → TMS_DISPATCHED|
   |                                    |                            | / TMS_REJECTED              |
   | 200 { status: "accepted" }        |                            |                              |
   |<──────────────────────────────────|                            |                              |
```

## 错误处理

| 场景 | HTTP | Response Body |
|------|------|---------------|
| `orderId` 不存在 | 404 | `{ "error": "order_not_found", "orderId": "ord-xxx" }` |
| 状态不是 `WMS_ACKED` | 409 | `{ "error": "invalid_order_status", "current": "CREATED", "expected": "WMS_ACKED" }` |
| 请求体验证失败 | 400 | 由全局 `@ControllerAdvice` 处理 |
| 成功 | 200 | `{ "status": "accepted" }` |

## 测试方案

### 单元测试

| 测试 | 文件 | 覆盖点 |
|------|------|--------|
| Order 新字段 | `OrderTest.java` | 构造/空列表/不可变性 |
| Persistence 新字段 | `OrderPersistenceAdapterTest.java` | JSON 序列化 round-trip |
| Callback controller | `WmsCallbackControllerTest.java` | 200/404/409/事件发布验证 |

### BDD 测试

#### 新增 feature: `place_order_wms_callback.feature`

专门验证新回调端点的行为，**独立于** TMS 流程：

```gherkin
Feature: WMS picking callback
  As a WMS system
  I want to notify the order service when picking is complete
  So that the order saga can proceed to TMS dispatch

  Background:
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction

  Scenario: Callback succeeds when order is in WMS_ACKED
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When the WMS callback is called with the order ID
    Then the callback response status should be 200
    And the order status should eventually be TMS_DISPATCHED

  Scenario: Callback returns 404 for unknown order ID
    When the WMS callback is called with order ID "non-existent-id"
    Then the callback response status should be 404

  Scenario: Callback returns 409 when order is not in WMS_ACKED state
    When the WMS callback is called with order ID "ord-pre-created"
    Then the callback response status should be 409
```

> `"ord-pre-created"` 在 step 中通过直接插入 DB 创建一个 CREATED 状态的订单。

#### 修复 `place_order_tms.feature` — 新增完整链路场景

在现有 3 个 TMS 场景前，加一个从下单到回调到 TMS 完成的全链路场景：

```gherkin
Feature: TMS dispatch flow

  Background:
    Given inventory service returns reservation success
    And WMS service accepts shipment instruction

  Scenario: Full saga lifecycle — place → WMS callback → TMS dispatch
    When the client submits a place order request
    Then the sync response status should be 201
    And the order status should eventually be WMS_ACKED
    When the WMS callback is called with the order ID
    Then the callback response status should be 200
    And the order status should eventually be TMS_DISPATCHED
    And the TMS service should receive a dispatch instruction

  # 以下 3 个是原有场景（修复后真正可跑）
  Scenario: TMS accepts dispatch after WMS picking completes
    ...
```

#### 新增 step definitions

`TmsSteps.java` 中补充：

```java
@When("the WMS callback is called with the order ID")
public void wmsCallbackIsCalledWithOrderId() {
    orderId = (String) PlaceOrderSteps.lastResponse.getBody().get("orderId");
    callbackResponse = httpHelper.postWmsPickingCallback(orderId);
}

@When("the WMS callback is called with order ID {string}")
public void wmsCallbackIsCalledWithOrderId(String predefinedOrderId) {
    callbackResponse = httpHelper.postWmsPickingCallback(predefinedOrderId);
}

@Then("the callback response status should be {int}")
public void theCallbackResponseStatusShouldBe(int statusCode) {
    assertThat(callbackResponse.getStatusCode().value()).isEqualTo(statusCode);
}
```

新增 `WmsCallbackSteps.java` 或放在 `TmsSteps.java`（建议放在 TmsSteps，因为逻辑耦合）。

#### 验证矩阵

修复后 BDD 测试总计从 **15 个场景 → 20 个场景**（+5）：

| # | Feature | 场景 | 状态 |
|---|---------|------|------|
| 1-3 | `place_order.feature` | 已有 3 场景 | ✅ 不变 |
| 4 | `place_order_wms_callback.feature` *(新)* | Callback 200 | **新增** |
| 5 | *(同上)* | Callback 404 | **新增** |
| 6 | *(同上)* | Callback 409 | **新增** |
| 7 | `place_order_tms.feature` *(修改)* | Full saga lifecycle (新增) | **新增** |
| 8-10 | *(同上)* | TMS 接受/拒绝/不可用 (原有) | ✅ 修复后可真实验证 |
| 11-13 | `place_order_validation.feature` | 已有 3 场景 | ✅ 不变 |
| 14-15 | `place_order_trace_id.feature` | 已有 2 场景 | ✅ 不变 |
| 16-17 | `place_order_multi_item.feature` | 已有 2 场景 | ✅ 不变 |
| 18-19 | `place_order_wms_failure.feature` | 已有 2 场景 | ✅ 不变 |
| 20 | `place_order_circuit_breaker.feature` | 已有 1 场景 | ✅ 不变 |

## 不在此次范围内

- Per-SKU OTel span 属性（Sprint 3-6，已有独立设计）
- 同步 WMS 回调重试/幂等（demo 项目暂不需要）
- `order-o11y` 模块的手动 OTel span（非此次修复目标）

## 后续待办（防止遗忘）

以下 BDD 缺口已识别但不在本次范围，记录在此避免丢失：

| # | 主题 | 描述 | 建议时机 |
|---|------|------|---------|
| 1 | 多商品 + TMS 补偿 | `place_order_multi_item.feature` 扩展到 TMS 阶段：多商品下单后 WMS 回调 → TMS 拒绝，验证 inventory 释放了所有商品的预占 | 后续 sprint |
| 2 | 可观测性 BDD | 验证 `o11y.server.requests`、`saga.step.duration` 等 Prometheus 指标在 BDD 场景后被记录 | Sprint 3（配合 v0.3.0-beta） |
| 3 | 弹性模式补充 | Circuit breaker 状态机转换（open→half-open→closed）、重试机制、限流超时 | 后续 sprint |

## 文件变更汇总

| 文件 | 操作 | 层级 |
|------|------|------|
| `Order.java` | 修改 | domain |
| `InventoryReservation.java` | 修改 | domain |
| `OrderPlacementSaga.java` | 修改 | domain |
| `OrderEntity.java` | 修改 | adapter/persistence |
| `OrderPersistenceAdapter.java` | 修改 | adapter/persistence |
| `WmsCallbackController.java` | 新增 | adapter/inbound/rest |
| `WmsCallbackRequest.java` | 新增 | adapter/inbound/rest |
| `OrderNotFoundException.java` | 新增 | adapter/inbound/rest |
| `V3__add_order_items_column.sql` | 修改 | infrastructure/db |
| `place_order_wms_callback.feature` | **新增** | bdd |
| `TmsSteps.java` | 修改 | bdd |
| `HttpHelper.java` | 修改 | bdd |
| `WmsCallbackSteps.java` | 新增（可选，逻辑可放 TmsSteps） | bdd |
| `OrderTest.java` | 修改 | domain-test |
| `OrderPersistenceAdapterTest.java` | 修改 | adapter-test |
| `WmsCallbackControllerTest.java` | 新增 | adapter-test |

## 架构合规性

- `WmsCallbackController` 在 `..adapter.inbound.rest..`，符合 ArchUnit `rest_controllers_should_reside_in_adapter_inbound_rest`
- 依赖 `OrderRepositoryPort`（`..application.port.out..`）和 `DomainEventPublisher`（`..application.port.out..`），不违反 `inbound_rest_should_not_depend_on_outbound`
- 无循环依赖新增
