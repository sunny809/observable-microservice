# 实现指南（Implementation Guide）

> 本文档面向需要理解、维护或扩展 `observable-microservice` / `order-demo` 的开发者。
> 它连接 `README.md`（快速入门）和 `ARCHITECTURE.md`（架构决策），用具体代码路径解释“功能是怎么实现的”。

---

## 1. 简介与读者

本项目是一个 **可观测性优先的 Spring Boot 微服务蓝图**，核心场景是“下单 Saga”：

```text
POST /api/v1/orders
  → 库存预占（Inventory）
  → 订单持久化（Order DB）
  → WMS 出库指令（Warehouse）
  → WMS 拣货完成
  → TMS 派运（Transport）
```

**读者假设**：
- 熟悉 Spring Boot、Maven、Java 21
- 了解微服务常见模式（Saga、熔断、幂等、分布式追踪）
- 不熟悉本仓库的具体代码组织

**本文档目标**：
- 说明技术栈与模块划分
- 解释六边形架构在代码中的落地
- 追踪一次请求从 Controller 到 Saga 再到外部适配器的完整链路
- 指出每个核心模式对应的实现文件
- 给出“如何新增一个端口/适配器/Saga 步骤”的实操指南

---

## 2. 技术栈一览

| 层级 | 技术 |
|------|------|
| 语言 / 构建 | Java 21，Maven 3.9+ |
| 框架 | Spring Boot 3.4.3，Spring Data JPA，Spring Security OAuth2 Resource Server |
| 可观测性 | OpenTelemetry 1.37，Micrometer + Prometheus，Logstash Encoder（结构化 JSON 日志） |
| 追踪后端 | Jaeger（OTLP gRPC，默认 `localhost:4317`） |
| 弹性 | Resilience4j（熔断、重试、限流、超时） |
| 持久化 | PostgreSQL 16 / H2，Flyway |
| 消息 | Kafka（WMS 消息队列适配器） |
| API 文档 | SpringDoc OpenAPI 2.5 |
| 测试 | JUnit 5，Mockito，AssertJ，Cucumber + WireMock，Testcontainers，ArchUnit |
| 代码质量 | JaCoCo（80% 行覆盖率），SpotBugs，Checkstyle |
| SDK | [o11y-kit](../o11y-kit (external dependency))（从本仓库抽离的可观测性 SDK） |

---

## 3. 模块职责与依赖规则

### 3.1 模块清单

```text
observable-microservice/
├── order-application/        # 纯领域层：业务逻辑、端口接口、Saga
├── order-adapter/            # 适配器层：REST、HTTP 客户端、JPA、Kafka、缓存
├── order-infrastructure/     # 基础设施入口：Spring Boot、OTel 配置、Flyway
├── (removed, merged into order-adapter)/               # OpenTelemetry 工具类（TracerHelper）
├── bdd-specs/                # Cucumber BDD 测试
└── o11y-kit (external dependency)                 # 抽离的可观测性 SDK
```

### 3.2 依赖方向（必须遵守）

```text
order-infrastructure → order-adapter → order-application → (removed, merged into order-adapter)
                                    ↘                    ↗
                                     o11y-kit（通过 starter 引入）
```

- **`order-application`** 不依赖任何其他模块，也不依赖 Spring 框架（除 `spring-tx`、`spring-context`、`jakarta.validation` 外）。
- **`order-adapter`** 依赖 `order-application` 和 `(removed, merged into order-adapter)`，并通过 `o11y-kit-spring-boot-starter` 引入可观测能力。
- **`order-infrastructure`** 依赖 `order-adapter`，负责启动 Spring Boot 和装配 Bean。

### 3.3 ArchUnit 强制规则

关键规则定义在：

```text
order-infrastructure/src/test/java/com/example/order/infrastructure/ArchitectureTest.java
```

| 规则 | 含义 |
|------|------|
| `application_layer_should_not_depend_on_adapter_or_infrastructure` | 领域层代码不能引用适配器/基础设施层 |
| `inbound_rest_should_not_depend_on_outbound` | 入站 REST 不能依赖出站适配器 |
| `rest_controllers_should_reside_in_adapter_inbound_rest` | `@RestController` 必须位于 `..adapter.inbound.rest..` |
| `entity_classes_should_reside_in_adapter_outbound_persistence` | `@Entity` 必须位于 `..adapter.outbound.persistence..` |
| `services_should_not_depend_on_jpa_or_webclient` | Service 不能依赖 JPA / WebClient / RestTemplate |
| `domain_events_should_be_immutable` | 领域事件类所有字段必须是 `final` |
| `ports_should_be_interfaces` | `..port..` 包下除命令/DTO 外必须是接口 |
| `no_cyclic_dependencies` | 顶层包之间不能循环依赖 |

---

## 4. 六边形架构实践

### 4.1 包约定

| 职责 | 包路径 |
|------|--------|
| 入站端口 | `com.order.demo.application.port.in.*` |
| 出站端口 | `com.order.demo.application.port.out.*` |
| 领域模型/事件 | `com.order.demo.application.domain.*` |
| Saga / 用例 | `com.order.demo.application.service.*` |
| REST 控制器 | `com.order.demo.adapter.inbound.rest.*` |
| HTTP 出站适配器 | `com.order.demo.adapter.outbound.inventory/wms/tms.*` |
| JPA 适配器 | `com.order.demo.adapter.outbound.persistence.*` |
| 缓存适配器 | `com.order.demo.adapter.outbound.cache.*` |
| 配置 / 指标 | `com.order.demo.adapter.config.*`、`com.order.demo.adapter.metrics.*` |
| 基础设施入口 | `com.order.demo.infrastructure.*` |

### 4.2 端口与适配器示例

**入站端口**（领域层定义）：

```text
order-application/src/main/java/com/example/order/application/port/in/PlaceOrderUseCase.java
```

```java
public interface PlaceOrderUseCase {
    OrderPlacedResult placeOrder(PlaceOrderCommand command);
}
```

**入站适配器**（REST 实现）：

```text
order-adapter/src/main/java/com/example/order/adapter/inbound/rest/OrderController.java
```

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final PlaceOrderUseCase placeOrderUseCase;
    // ...
}
```

**出站端口**（领域层定义）：

```text
order-application/src/main/java/com/example/order/application/port/out/InventoryPort.java
```

```java
public interface InventoryPort {
    CompletableFuture<InventoryReservation> occupy(ReservationRequest request);
    CompletableFuture<Void> release(String reservationId);
    CompletableFuture<Void> confirm(ConfirmReservationCommand request);
}
```

**出站适配器**（WebClient 实现）：

```text
order-adapter/src/main/java/com/example/order/adapter/outbound/inventory/InventoryRestAdapter.java
```

```java
@Component
public class InventoryRestAdapter implements InventoryPort {
    // WebClient + Resilience4j + OpenTelemetry 手动埋点
}
```

---

## 5. 核心代码入口

### 5.1 REST 入口：OrderController

文件：

```text
order-adapter/src/main/java/com/example/order/adapter/inbound/rest/OrderController.java
```

关键点：

- `@RateLimiter(name = "orderPlacement")`：限流 100 次/分钟（配置见 `application.yml`）。
- `@PreAuthorize("hasAuthority('SCOPE_order:write')")`：需要 JWT scope `order:write`。
- `@Traced(spanName = SpanNames.ORDER_PLACEMENT)`：手动创建 OpenTelemetry span。
- 将 `PlaceOrderRequest` DTO 转成 `PlaceOrderCommand`，调用 `PlaceOrderUseCase.placeOrder()`。

### 5.2 Saga 编排：OrderPlacementSaga

文件：

```text
order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java
```

状态机：

```text
CREATED → WMS_ACKED → WMS_PICKED → TMS_DISPATCHED
   ↓          ↓             ↓
REJECTED  REJECTED    TMS_REJECTED
```

核心方法：

| 方法 | 触发方式 | 职责 |
|------|----------|------|
| `placeOrder()` | 同步调用 | 幂等校验、库存预占、订单持久化、发布 WMS 事件 |
| `onWmsRequired()` | `@TransactionalEventListener(AFTER_COMMIT)` | 发送 WMS 指令、确认库存、更新状态 |
| `onWmsPickingCompleted()` | `@TransactionalEventListener(AFTER_COMMIT)` | WMS 拣货完成 → 发布 TMS 事件 |
| `onTmsRequired()` | `@TransactionalEventListener(AFTER_COMMIT)` | 发送 TMS 指令、更新状态 |

### 5.3 关键出站适配器

| 适配器 | 文件 | 说明 |
|--------|------|------|
| InventoryRestAdapter | `order-adapter/.../outbound/inventory/InventoryRestAdapter.java` | WebClient + 熔断/重试 + OTel 手动 span |
| WmsRestAdapter | `order-adapter/.../outbound/wms/WmsRestAdapter.java` | REST 调用 WMS |
| WmsRestTemplateAdapter | `order-adapter/.../outbound/wms/WmsRestTemplateAdapter.java` | RestTemplate 实现（对比用） |
| WmsMessageQueueAdapter | `order-adapter/.../outbound/wms/WmsMessageQueueAdapter.java` | Kafka 发送 WMS 指令 |
| TmsRestAdapter | `order-adapter/.../outbound/tms/TmsRestAdapter.java` | REST 调用 TMS |
| OrderPersistenceAdapter | `order-adapter/.../outbound/persistence/OrderPersistenceAdapter.java` | JPA 持久化，Jackson Mixin 映射领域对象 |
| IdempotencyCacheAdapter | `order-adapter/.../outbound/cache/IdempotencyCacheAdapter.java` | Caffeine 幂等缓存 |

---

## 6. Saga 流程详解

### 6.1 同步阶段：`placeOrder()`

1. **幂等校验**：先查 Caffeine 缓存，命中再查 DB；若存在则抛 `DuplicateOrderException`。
2. **库存预占**：调用 `inventoryPort.occupy(...)` 对每个 SKU 顺序预占；任一失败即释放已预占库存。
3. **订单持久化**：生成 `orderId`，创建 `Order` 聚合根，状态为 `CREATED`。
4. **缓存写入**：将幂等键写入 Caffeine。
5. **发布领域事件**：`WmsInstructionRequiredEvent`，由 `@TransactionalEventListener(AFTER_COMMIT)` 异步处理。

```java
@Transactional
public OrderPlacedResult placeOrder(PlaceOrderCommand command) {
    // 1. 幂等校验
    // 2. reserveAllItems(command, orderId)
    // 3. orderRepository.save(order)
    // 4. idempotencyCache.put(...)
    // 5. eventPublisher.publish(new WmsInstructionRequiredEvent(...))
}
```

### 6.2 异步阶段：WMS / TMS

以 `onWmsRequired()` 为例：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onWmsRequired(WmsInstructionRequiredEvent event) {
    wmsPort.sendInstruction(event.getInstruction())
        .thenCompose(ack -> {
            if (ack.isAccepted()) {
                // 确认库存 + TransactionTemplate 更新 DB
            } else {
                // 释放库存 + TransactionTemplate 标记 REJECTED
            }
        })
        .exceptionally(ex -> {
            // 释放库存 + TransactionTemplate 标记 REJECTED
        });
}
```

**为什么用 `TransactionTemplate`**：
`AFTER_COMMIT` 监听器运行在 Reactor Netty 线程，没有活跃的 Spring 事务。所有 DB 操作必须包在 `transactionTemplate.executeWithoutResult(...)` 中（见 `ARCHITECTURE.md` ADR-1）。

### 6.3 补偿逻辑

- `releaseAll()`：同步释放所有库存预占，异常不传播，避免影响其他释放。
- `releaseAllAsync()`：异步批量释放，每个失败单独记录补偿日志。

---

## 7. 幂等性实现

**双层级**：

1. **Caffeine 缓存**（快路径）
   - TTL：30 分钟
   - 容量：10,000 条
   - 文件：`order-adapter/.../outbound/cache/IdempotencyCacheAdapter.java`
2. **数据库唯一约束**（真相源）
   - `OrderRepositoryPort.findByIdempotencyKey(...)`
   - 文件：`order-adapter/.../outbound/persistence/OrderRepositoryPort.java`

**时序**：

```java
if (cache.exists(key)) {
    db.findByIdempotencyKey(key).ifPresent(order -> throw DuplicateOrderException);
} else {
    db.findByIdempotencyKey(key).ifPresent(order -> throw DuplicateOrderException);
}
```

> 详见 `ARCHITECTURE.md` ADR-4。

---

## 8. 弹性模式

Resilience4j 配置在：

```text
order-infrastructure/src/main/resources/application.yml
```

### 8.1 熔断器（Circuit Breaker）

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 5s
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        waitDurationInOpenState: 30s
```

- 失败率 ≥ 50% 或慢调用率 ≥ 80% 时打开熔断。
- 打开状态持续 30 秒，半开允许 3 次探测。

### 8.2 重试（Retry）

```yaml
resilience4j:
  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```

### 8.3 限流（Rate Limiter）

作用于 `OrderController.placeOrder()`：

```yaml
resilience4j:
  ratelimiter:
    instances:
      orderPlacement:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
```

### 8.4 超时（Time Limiter）

```yaml
resilience4j:
  timelimiter:
    instances:
      inventoryService:
        timeoutDuration: 10s
```

### 8.5 Fallback

每个 `@CircuitBreaker` 都有 `fallbackMethod`，返回 `CompletableFuture.failedFuture(...)`，把异常传回 Saga 的 `exceptionally` 分支处理。

---

## 9. 可观测性

### 9.1 OpenTelemetry SDK 配置

文件：

```text
order-infrastructure/src/main/java/com/example/order/infrastructure/config/OpenTelemetryConfig.java
```

- OTLP gRPC exporter，默认 endpoint `http://localhost:4317`
- `BatchSpanProcessor` 批量导出到 Jaeger
- 注册全局 `OpenTelemetry` 实例，供 `TracerHelper.getTracer()` 使用

### 9.2 手动埋点（适配器层）

以 `InventoryRestAdapter` 为例：

```java
Span span = tracer.spanBuilder(SpanNames.INVENTORY_OCCUPY).startSpan();
try (Scope scope = span.makeCurrent()) {
    return webClient.post()
        // ...
        .doFinally(sig -> span.end())
        .toFuture();
}
```

> span 通过 `doFinally` 在 Mono 完成时结束，覆盖成功、失败、取消（见 ADR-3）。

### 9.3 o11y-kit 自动埋点

通过 starter 引入后自动生效：

| 组件 | 文件 | 能力 |
|------|------|------|
| ServerObservationHandler | `o11y-kit (external dependency).../spring/webmvc/ServerObservationHandler.java` | MVC 请求计时、traceId 解析、响应头注入 |
| ClientObservationHandler | `o11y-kit (external dependency).../spring/webflux/ClientObservationHandler.java` | WebClient 调用计时、子 span |
| RestTemplateObservationInterceptor | `o11y-kit (external dependency).../spring/webmvc/client/RestTemplateObservationInterceptor.java` | RestTemplate 调用计时 |
| RestClientObservationInterceptor | `o11y-kit (external dependency).../spring/webmvc/client/RestClientObservationInterceptor.java` | RestClient 调用计时 |

### 9.4 指标

**o11y-kit 自动指标**：

| Metric | Type | Tags |
|--------|------|------|
| `o11y.server.requests` | Timer | `method`, `uri`, `status` |
| `o11y.client.requests` | Timer | `method`, `host`, `status` |
| `o11y.client.errors` | Counter | `method`, `host`, `error` |

**业务自定义指标**（当前未全部接入 Saga，见代码审查结果）：

文件：

```text
order-adapter/src/main/java/com/example/order/adapter/metrics/OrderMetrics.java
```

| Metric | Type | Tags |
|--------|------|------|
| `orders.placed` | Counter | `status` |
| `orders.failed` | Counter | `reason` |
| `inventory.reservation` | Counter | `sku`, `result` |
| `saga.duration` | Timer | `outcome` |

### 9.5 结构化日志

使用 Logstash Encoder 输出 JSON，包含 `traceId`、`service`、`version` 等字段。Controller 中通过 `MDC.get("traceId")` 获取当前 traceId 并返回给客户端。

---

## 10. 异步事件处理

### 10.1 事件发布

`SpringDomainEventPublisher` 实现 `DomainEventPublisher`：

```text
order-infrastructure/src/main/java/com/example/order/infrastructure/adapter/SpringDomainEventPublisher.java
```

### 10.2 事件监听

Saga 中的三个方法都使用：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

保证：
- 订单事务提交后才触发外部调用
- 外部调用失败不影响主事务
- 回调中通过 `TransactionTemplate` 开启新事务更新订单状态

---

## 11. 安全

### 11.1 配置类

| 文件 | 用途 |
|------|------|
| `order-adapter/.../config/SecurityConfig.java` | JWT Resource Server，生产环境生效 |
| `order-adapter/.../config/LocalSecurityConfig.java` | 本地开发免 JWT |
| `order-adapter/.../config/TestSecurityConfig.java` | 测试环境放行 |

### 11.2 权限

- `POST /api/v1/orders` 需要 `SCOPE_order:write`
- Actuator、Swagger、OpenAPI 端点公开访问

---

## 12. 测试策略

### 12.1 单元测试

每个模块独立运行：`mvn test`

### 12.2 ArchUnit 架构测试

```bash
mvn -pl order-infrastructure test -Dtest=ArchitectureTest
```

### 12.3 BDD / Cucumber

```text
bdd-specs/src/test/java/com/example/order/specs/
```

- WireMock 虚拟 Inventory / WMS / TMS 服务
- Testcontainers 启动 PostgreSQL
- 覆盖完整下单 Saga 成功/失败路径

运行：

```bash
mvn -pl bdd-specs test -Dtest=CucumberTestSuite
```

### 12.4 o11y-kit 测试

```bash
cd o11y-kit && mvn test
```

---

## 13. 扩展指南

### 13.1 新增一个出站端口

1. 在 `order-application/src/main/java/com/example/order/application/port/out/` 创建接口，例如 `PaymentPort.java`。
2. 在 `order-application` 的 Saga 或 Service 中通过构造器注入使用。
3. 在 `order-adapter/src/main/java/com/example/order/adapter/outbound/payment/` 实现该接口。
4. 在 `order-infrastructure` 中确保该 Bean 被 Spring 扫描（组件已用 `@Component` 则自动扫描）。
5. 更新 `ArchitectureTest` 如果需要新增包规则。

### 13.2 新增一个 REST 端点

1. 在 `order-application/port/in/` 定义入站端口。
2. `order-application/service/` 实现业务逻辑。
3. `order-adapter/inbound/rest/` 新增 Controller，依赖入站端口。
4. 添加 ArchUnit 规则或依赖现有规则校验包位置。

### 13.3 新增一个 Saga 步骤

1. 在 `order-application/domain/` 新增领域事件，例如 `InvoiceRequiredEvent`。
2. 在上一步监听器中 `eventPublisher.publish(...)` 发布新事件。
3. 在 `OrderPlacementSaga` 中新增 `@TransactionalEventListener(AFTER_COMMIT)` 方法处理该事件。
4. 新增出站端口/适配器调用外部服务。
5. 更新 BDD feature 文件覆盖新流程。

---

## 14. 构建与运行

### 14.1 常用命令

```bash
# 启动本地依赖（Jaeger、PostgreSQL）
make dev

# 构建 order-demo
make build

# 同时构建 o11y-kit + order-demo
make build-all

# 运行测试
make test

# 运行 BDD
make test-bdd

# 运行架构测试
make test-arch

# 启动应用
make run

# 带 profile 启动
make run-profile PROFILE=local
```

### 14.2 Docker Compose 服务

```bash
docker-compose up -d
```

包含：Jaeger、PostgreSQL、Zookeeper、Kafka、TMS mock。

### 14.3 关键端点

| 服务 | URL |
|------|-----|
| Order API | `http://localhost:8080/api/v1/orders` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |
| Prometheus | `http://localhost:8080/actuator/prometheus` |
| Jaeger UI | `http://localhost:16686` |

---

## 15. 常见陷阱与延伸阅读

| 主题 | 参考文档 |
|------|----------|
| 架构决策（ADR） | [`ARCHITECTURE.md`](../ARCHITECTURE.md) |
| 经验教训 | [`docs/LESSONS_LEARNED.md`](LESSONS_LEARNED.md) |
| 问题排查 | [`docs/TROUBLESHOOTING.md`](TROUBLESHOOTING.md) |
| 迁移说明 | [`docs/MIGRATION.md`](MIGRATION.md) |
| 路线图 | [`docs/ROADMAP.md`](ROADMAP.md) |
| o11y-kit SDK 规范 | [`docs/SPEC.md`](SPEC.md) |
| o11y-kit 使用文档 | [`o11y-kit (external dependency)README.md`](../o11y-kit (external dependency)README.md) |

---

## 16. 关键文件速查

| 关注点 | 路径 |
|--------|------|
| 架构规则 | `order-infrastructure/src/test/java/com/example/order/infrastructure/ArchitectureTest.java` |
| REST 入口 | `order-adapter/src/main/java/com/example/order/adapter/inbound/rest/OrderController.java` |
| Saga 编排 | `order-application/src/main/java/com/example/order/application/service/OrderPlacementSaga.java` |
| 入站端口 | `order-application/src/main/java/com/example/order/application/port/in/` |
| 出站端口 | `order-application/src/main/java/com/example/order/application/port/out/` |
| 库存适配器 | `order-adapter/src/main/java/com/example/order/adapter/outbound/inventory/InventoryRestAdapter.java` |
| 持久化适配器 | `order-adapter/src/main/java/com/example/order/adapter/outbound/persistence/OrderPersistenceAdapter.java` |
| 幂等缓存 | `order-adapter/src/main/java/com/example/order/adapter/outbound/cache/IdempotencyCacheAdapter.java` |
| 业务指标 | `order-adapter/src/main/java/com/example/order/adapter/metrics/OrderMetrics.java` |
| OTel 配置 | `order-infrastructure/src/main/java/com/example/order/infrastructure/config/OpenTelemetryConfig.java` |
| 应用配置 | `order-infrastructure/src/main/resources/application.yml` |
| 启动类 | `order-infrastructure/src/main/java/com/example/order/infrastructure/OrderServiceApplication.java` |
| BDD 测试 | `bdd-specs/src/test/java/com/example/order/specs/` |
