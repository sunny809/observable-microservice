# Test Design — Sprint 3: Internal Transaction Observability

**Author:** QA
**Date:** 2026-06-23
**Status:** Approved

---

## 1. Scope

**In scope:** All 7 User Stories from Sprint 3 (US-3.1 to US-3.7).
**Out of scope:** Performance/load testing, mutation testing (Sprint 4).

## 2. Test Strategy

| US | Unit Tests | Integration Tests | BDD | Manual Verify |
|----|-----------|-------------------|-----|---------------|
| US-3.1 | ✅ OrderControllerTest + SagaTest | ✅ Spring context metrics verify | ❌ | ✅ curl /actuator/prometheus |
| US-3.2 | ✅ SagaLogPersistenceAdapterTest | ✅ SagaLogJpaRepositoryIntegrationTest | ❌ | — |
| US-3.3 | ✅ OrderPlacementSagaTest | ✅ Application context test | ❌ | ✅ curl /actuator/prometheus |
| US-3.4 | ✅ OrderPlacementSagaTest | ❌ | ❌ | ✅ Verify gaps in Prometheus |
| US-3.5 | ❌ | ❌ | ❌ | ✅ Verify log format in stdout |
| US-3.6 | ❌ | ❌ | ❌ | ✅ Verify span attr in Jaeger |
| US-3.7 | ✅ All new test code | ❌ | ❌ | — |

## 3. Test Scenarios

### US-3.1: 修复 OrderMetrics 死代码

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 1.1 | 成功下单，指标计数正确 | 1. Mock `PlaceOrderUseCase` 返回成功<br>2. 调用 `OrderController.placeOrder()` | `recordOrderPlaced("CREATED")` 被调用 | Positive |
| 1.2 | 重复订单，指标计数正确 | 1. Mock use case 抛 `DuplicateOrderException`<br>2. 调用 controller | `recordOrderFailed("DUPLICATE_ORDER")` 被调用 | Negative |
| 1.3 | 库存不足，指标计数正确 | 1. Mock use case 抛 `InsufficientInventoryException`<br>2. 调用 controller | `recordOrderFailed("INSUFFICIENT_INVENTORY")` 被调用 | Negative |
| 1.4 | 真实 Spring 上下文中 prometheus 端点可查 | 1. 启动 `@SpringBootTest`<br>2. POST 下单<br>3. GET `/actuator/prometheus` | 返回包含 `orders_placed_total` | Integration |

### US-3.2: 扩展 SagaLogPort

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 2.1 | `recordStep(4args)` 转发到 Metrics | 1. Mock `OrderMetrics`<br>2. 调用 adapter.recordStep(id, step, detail, 100, "success") | `orderMetrics.recordSagaStep("SYNC_PHASE", 100, "success")` 被调用 | Positive |
| 2.2 | `recordGap()` 转发到 Metrics | 1. Mock `OrderMetrics`<br>2. 调用 adapter.recordGap(id, "POST_COMMIT_TO_WMS", 15) | `orderMetrics.recordSagaGap("POST_COMMIT_TO_WMS", 15)` 被调用 | Positive |
| 2.3 | 旧 3 参数重载保持兼容 | 1. Mock `OrderMetrics`<br>2. 调用旧的 `recordStep(id, step, detail)` | 无新 metrics 调用，仅 DB 写 | Regression |
| 2.4 | ECS 日志输出 | 1. Capture logger output<br>2. 调用 `recordStep(4args)` | JSON 包含 `event.action=saga.step.end`, `event.duration=100000000` | Positive |

### US-3.3: 三步 saga 阶段耗时

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 3.1 | 正常路径三步皆完成 | 1. Mock 所有外部服务成功<br>2. 执行 saga<br>3. 验证 | 3 条 `recordStep(4args)` 调用，步骤为 SYNC_PHASE/WMS_PHASE/TMS_PHASE，耗时 >0 | Positive |
| 3.2 | WMS 拒绝触发补偿 | 1. Mock WMS 返回 `ack.isAccepted=false`<br>2. 执行 saga<br>3. 验证 | SYNC_PHASE 成功 + WMS_PHASE + 补偿；outcome="compensation" | Negative |
| 3.3 | WMS 网络异常触发失败 | 1. Mock WMS 抛异常<br>2. 执行 saga<br>3. 验证 | SYNC_PHASE 成功 + WMS_PHASE outcome="failure" | Negative |

### US-3.4: 异步等待间隙

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 4.1 | gap 记录正常输出 | 1. 执行完整 saga<br>2. 验证 | `recordGap()` 至少被调用 1 次，gap 名称正确 | Positive |
| 4.2 | gap 值 >0 | 1. 执行完整 saga<br>2. 验证 | gap 时间戳差 >0 | Edge |

### US-3.5: ECS 日志格式

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 5.1 | JSON 格式含 ECS 字段 | 1. 启动应用<br>2. 下单<br>3. 查看 stdout | 日志行包含 `"event":{"action":"saga.step.end","duration":...}` | Manual |
| 5.2 | traceId 关联 | 1. 下单抓 traceId<br>2. 搜索日志 | 同一 traceId 下有 saga.step.begin 和 saga.step.end 配对 | Manual |

### US-3.6: Per-SKU span attribute

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 6.1 | OTel span 含 SKU 属性 | 1. 启动带 Jaeger exporter<br>2. 下单<br>3. Jaeger 查找 trace | `inventory.occupy` span 有 `sku="SKU-1"` | Manual |
| 6.2 | Prometheus 基数不受影响 | 1. 查询 `/actuator/prometheus` | `o11y_client_requests` tag 集合不变 | Manual |

## 4. Test Data

| Data Type | Value | Purpose |
|-----------|-------|---------|
| Customer ID | `cust-1` | Normal path test |
| Idempotency Key | `order-001` | Normal path test |
| SKU | `SKU-1` | Single item order |
| SKU | `SKU-OUT-OF-STOCK` | Inventory failure test (if applicable) |

## 5. Environment Dependencies

| Dependency | Type | Config |
|-----------|------|--------|
| H2 in-memory DB | Embedded | `application-test.yml` |
| MockMvc | Spring test | `@AutoConfigureMockMvc` |
| WireMock (Inventory/WMS/TMS) | Mocked | BDD profile |
| Prometheus MeterRegistry | Micrometer test | `SimpleMeterRegistry` for assertions |

## 6. Acceptance Criteria Mapping

| US | AC | Test Scenario # | Status |
|----|----|-----------------|--------|
| US-3.1 | AC1 | 1.1, 1.4 | ⏳ Planned |
| | AC2 | 1.2 | ⏳ Planned |
| | AC3 | 1.3 | ⏳ Planned |
| US-3.2 | AC1 | 2.1 | ⏳ Planned |
| | AC2 | 2.3 | ⏳ Planned |
| | AC3 | 2.1 + 2.4 | ⏳ Planned |
| | AC4 | 2.2 | ⏳ Planned |
| US-3.3 | AC1 | 3.1 (SYNC) | ⏳ Planned |
| | AC2 | 3.1 (WMS, TMS) | ⏳ Planned |
| | AC3 | 3.2, 3.3 | ⏳ Planned |
| US-3.4 | AC1 | 4.1 | ⏳ Planned |
| | AC2 | 4.2 | ⏳ Planned |
| US-3.5 | AC1-4 | 5.1, 5.2 | ⏳ Planned |
| US-3.6 | AC1-3 | 6.1, 6.2 | ⏳ Planned |

## 7. Regression Concerns

- `OrderPlacementSagaTest.java`: existing test assertions may need update if mock interactions change
- BDD features: `place_order.feature`, `inventory_failure.feature`, `tms_dispatch.feature` — no changes expected
- ArchUnit: no new architecture rules needed
- JaCoCo threshold (80%): new code in `OrderMetrics`, `SagaLogPersistenceAdapter`, `OrderController`, `OrderPlacementSaga` must be ≥80% covered