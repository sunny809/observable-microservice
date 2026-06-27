# Sprint 3 Backlog — Internal Transaction Observability

**Version:** v0.3.0-beta

**Sprint Goal:**
Make every internal step of a saga transaction visible in Prometheus + Jaeger + ELK.
Stakeholders can answer: "Why did this order take 3 seconds? Was it inventory, WMS, or idle time between steps?"

**Theme:** Internal transaction latency breakdown

**Duration:** 3.7 days engineering; target 2026-09

**Total stories:** 7 (P0: 3, P1: 2, P2: 2)

---

## User Stories

### US-3.1: [P0] 修复 OrderMetrics 死代码 — 接入 saga 流程

- **Priority:** P0
- **Effort:** S (0.5d)
- **Area:** order-adapter

**As a** DevOps engineer
**I want** `orders.placed`, `orders.failed`, `inventory.reservation`, `saga.duration` 指标在 Prometheus 真实可查
**So that** 业务大盘能正确反映订单情况，而非永远为 0

**Acceptance Criteria:**
- [ ] AC1: 成功下单后 `orders_placed_total{status="CREATED"}` 计数 +1
- [ ] AC2: 重复请求/幂等性命中后 `orders_failed_total{reason="DUPLICATE_ORDER"}` 计数 +1
- [ ] AC3: 库存不足时 `orders_failed_total{reason="INSUFFICIENT_INVENTORY"}` 计数 +1
- [ ] AC4: 完成完整 saga 循环后 `saga_duration_seconds_count{outcome="success"}` ≥1

**Tech notes:**
- 涉及文件: `OrderMetrics.java`, `OrderController.java`, `OrderPlacementSaga.java`
- OrderMetrics 在 adapter 层，不能直接注入 saga（architecture 约束）。通过扩展 SagaLogPort（US-3.2）间接接入
- Controller 层可以直接注入 OrderMetrics 走同步路径

---

### US-3.2: [P0] 扩展 SagaLogPort 接口 — 增加耗时 + gap 方法

- **Priority:** P0
- **Effort:** S (1d)
- **Area:** order-application + order-adapter

**As a** Tech-Lead
**I want** `SagaLogPort` 接口新增带耗时/outcome 参数的 `recordStep()` 重载以及 `recordGap()` 方法
**So that** saga 可以在不引入 Micrometer 依赖的前提下传递耗时数据到 adapter 层

**Acceptance Criteria:**
- [ ] AC1: `SagaLogPort` 编译通过，新增方法签名正确
- [ ] AC2: 旧 3 参数 `recordStep()` 调用不受影响（向后兼容）
- [ ] AC3: `SagaLogPersistenceAdapter` 实现将耗时数据转发到 OrderMetrics（Micrometer）和结构化日志（ECS）
- [ ] AC4: `recordGap()` 正确记录到 `saga.gap.duration` Timer

**Tech notes:**
- 涉及文件: `SagaLogPort.java`, `SagaLogPersistenceAdapter.java`, `OrderMetrics.java`
- 这是 architecture 关键决策：通过 port/adapter 模式解耦 domain 和 metrics

---

### US-3.3: [P0] 三步 saga 阶段耗时统计

- **Priority:** P0
- **Effort:** M (1d)
- **Area:** order-application

**As a** Stakeholder
**I want** 每个 saga 阶段（同步/WMS/TMS）的耗时独立可查
**So that** 能定位瓶颈是在库存预占、WMS 发单还是 TMS 发运

**Acceptance Criteria:**
- [ ] AC1: `/actuator/prometheus` 返回 `saga_step_duration_seconds{step="SYNC_PHASE",outcome="success"}`
- [ ] AC2: 同上，WMS_PHASE 和 TMS_PHASE 存在
- [ ] AC3: 补偿/失败路径的 `outcome` 标记为 `"failure"` 或 `"compensation"`
- [ ] AC4: 耗时单位为秒（Prometheus 最佳实践），p50/p95/p99 可计算

**Tech notes:**
- 依赖 US-3.2（SagaLogPort 扩展完成后方可开始）
- 涉及文件: `OrderPlacementSaga.java`, `OrderMetrics.java`

---

### US-3.4: [P1] 异步等待间隙耗时测量

- **Priority:** P1
- **Effort:** M (0.5d)
- **Area:** order-application

**As a** SRE
**I want** 测量 async phase 之间微服务"空等"的时间（POST_COMMIT_TO_WMS、WMS_ACKED_TO_PICKED、PICKED_TO_TMS）
**So that** 能区分"外部服务处理慢"和"内部事件调度延迟"

**Acceptance Criteria:**
- [ ] AC1: `saga_gap_duration_seconds{gap="POST_COMMIT_TO_WMS"}` ≥0
- [ ] AC2: `saga_gap_duration_seconds{gap="WMS_ACKED_TO_PICKED"}` ≥0
- [ ] AC3: gap 时间与 step 时间可对比（总和 = 端到端延迟）

**Tech notes:**
- 依赖 US-3.2（recordGap 方法）
- 在 `@TransactionalEventListener` 入口处记录时间戳差

---

### US-3.5: [P1] ECS 结构化日志格式

- **Priority:** P1
- **Effort:** S (0.5d)
- **Area:** order-infrastructure

**As a** DevOps engineer
**I want** saga step 事件以 ECS（Elastic Common Schema）格式输出到 stdout
**So that** ELK pipeline 可零转型直接消费

**Acceptance Criteria:**
- [ ] AC1: 日志 JSON 包含 `event.action`（`saga.step.begin` / `saga.step.end` / `saga.gap`）
- [ ] AC2: 日志 JSON 包含 `event.duration`（纳秒，ECS 标准）
- [ ] AC3: 日志 JSON 包含 `saga.order_id`, `saga.step`, `saga.outcome`
- [ ] AC4: 现有非 ECS 字段（`@timestamp`, `message`, `level`）保持不变

**Tech notes:**
- 涉及文件: `logback-spring.xml`
- 使用 LogstashEncoder 的 fieldNames 配置 + StructuredArguments
- 参考 ECS 8.0 标准

---

### US-3.6: [P2] Per-SKU OTel span attribute

- **Priority:** P2
- **Effort:** XS (0.2d)
- **Area:** order-adapter

**As a** Developer
**I want** inventory occupy 的 OTel span 包含 `sku` 和 `item.quantity` 属性
**So that** 在 Jaeger 中可以按 SKU 过滤和查看单个商品预占耗时（不增加 Prometheus 基数）

**Acceptance Criteria:**
- [ ] AC1: Jaeger trace 中 `inventory.occupy` span 有 `sku="SKU-1"` 属性
- [ ] AC2: `o11y.client.requests` metric 不受影响（无新 tag）
- [ ] AC3: 高基数场景下 Prometheus 不增加新时间序列

**Tech notes:**
- 涉及文件: `InventoryRestAdapter.java`
- 加一行代码: `span.setAttribute("sku", request.getSku())`
- 低基数方案：只写 span attribute，不写 Prometheus tag

---

### US-3.7: [P2] 单元测试 — saga step metrics 验证

- **Priority:** P2
- **Effort:** M (1d)
- **Area:** order-application + order-adapter

**As a** QA
**I want** 所有新增的 metrics 记录路径都有单元测试覆盖
**So that** 重构时不会意外丢失指标采集

**Acceptance Criteria:**
- [ ] AC1: `SagaLogPersistenceAdapterTest` 验证 `recordStep(duration, outcome)` 调用 `OrderMetrics.recordSagaStep()`
- [ ] AC2: 验证 `recordGap()` 调用 `OrderMetrics.recordSagaGap()`
- [ ] AC3: JaCoCo 行覆盖率 ≥80%（新增代码）
- [ ] AC4: 现有测试不产生回归

**Tech notes:**
- 涉及文件: `SagaLogPersistenceAdapterTest.java`, `OrderPlacementSagaTest.java`
- 使用 MockMeterRegistry 断言 Micrometer 交互

---

## Effort Summary

| Story | Priority | Area | Effort | Dependencies |
|-------|----------|------|--------|-------------|
| US-3.1 | P0 | order-adapter | S (0.5d) | None |
| US-3.2 | P0 | order-application + adapter | S (1d) | None |
| US-3.3 | P0 | order-application | M (1d) | US-3.2 |
| US-3.4 | P1 | order-application | M (0.5d) | US-3.2 |
| US-3.5 | P1 | order-infrastructure | S (0.5d) | None |
| US-3.6 | P2 | order-adapter | XS (0.2d) | None |
| US-3.7 | P2 | order-application + adapter | M (1d) | US-3.2, US-3.3 |

**Total:** 3.7 person-days

**Dependency graph:**
```
US-3.1 (no deps) ──────┐
                        ├── US-3.3 ──┐
US-3.2 (no deps) ──────┘           ├── US-3.7
                                    │
US-3.4 ──(depends on US-3.2)───────┘
US-3.5 (no deps)
US-3.6 (no deps)
```