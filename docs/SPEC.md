# SPEC: o11y-kit — 面向 Spring Boot 的轻量级可观测性 SDK

## 目标用户

Java / Spring Boot 后端开发者，希望在项目中**零侵入**地获得：
- Controller 请求的 round-trip 耗时 & HTTP 状态码分布
- WebClient 调用外部服务的 round-trip 耗时 & HTTP 状态码分布
- 一致的 traceId 透传（B3 / W3C traceparent）
- 无需在每个 Adapter 里手写 `spanBuilder` + `span.end()`

## 核心流程

```
┌─ Controller ──────────────────────┐
│  HandlerInterceptor               │
│  preHandle  : 记录 startTime      │
│  afterCompletion: 计算耗时 +      │
│    记录 statusCode → Counter      │
│    记录 latency → Timer           │
└───────────────────────────────────┘

┌─ WebClient ───────────────────────┐
│  ExchangeFilterFunction           │
│  request   : 记录 startTime      │
│  response  : 计算耗时 +          │
│    记录 statusCode → Counter      │
│    记录 latency → Timer           │
│    创建子 Span                    │
└───────────────────────────────────┘

┌─ TraceId 透传 ────────────────────┐
│  Servlet Filter / WebClient Filter │
│  inbound : 解析 B3/w3c → MDC      │
│  outbound: 注入 B3/w3c 到请求头    │
└───────────────────────────────────┘
```

## 非目标

- ❌ 不替代 OpenTelemetry / Micrometer — 底层仍用它们
- ❌ 不提供日志库（Logback/Log4j 集成）
- ❌ 不支持 gRPC / Thrift（仅 HTTP request-response）
- ❌ 不提供告警规则或 Dashboard 定义
- ❌ 不处理 Kafka / RabbitMQ 消息追踪
- ❌ 不提供数据库查询性能追踪（JDBC / JPA）
- ❌ 不提供缓存调用追踪（Redis / Caffeine）

## 架构分层

```
┌─────────────────────────────────────────────────────────┐
│                   o11y-kit-api                           │
│  (io.o11y.kit:o11y-kit-api, 零框架依赖)                   │
│                                                          │
│  HttpMetricRecorder (interface)         核心抽象          │
│  TraceIdResolver (utility)              工具类            │
└──────────────────────┬───────────────────────────────────┘
                       │ implements
┌──────────────────────▼───────────────────────────────────┐
│              o11y-kit-micrometer                          │
│  (io.o11y.kit:o11y-kit-micrometer)                       │
│                                                          │
│  MicrometerHttpMetricRecorder  Timer+Counter 实现         │
└──────────────────────┬───────────────────────────────────┘
                       │ used by
┌──────────────────────▼───────────────────────────────────┐
│              o11y-kit-spring-webmvc                       │
│  (io.o11y.kit:o11y-kit-spring-webmvc)                    │
│                                                          │
│  ServerObservationHandler     HandlerInterceptor          │
└──────────────────────┬───────────────────────────────────┘
                       │ used by
┌──────────────────────▼───────────────────────────────────┐
│              o11y-kit-spring-webflux                      │
│  (io.o11y.kit:o11y-kit-spring-webflux)                   │
│                                                          │
│  ClientObservationHandler     ExchangeFilterFunction      │
└──────────────────────┬───────────────────────────────────┘
                       │ auto-configured by
┌──────────────────────▼───────────────────────────────────┐
│          o11y-kit-spring-boot-autoconfigure               │
│  (io.o11y.kit:o11y-kit-spring-boot-autoconfigure)        │
│                                                          │
│  HttpMetricsAutoConfiguration       @ConditionalOnBean   │
│  ObservationWebMvcAutoConfiguration WebMvcConfigurer      │
└──────────────────────┬───────────────────────────────────┘
                       │ aggregated by
┌──────────────────────▼───────────────────────────────────┐
│            o11y-kit-spring-boot-starter                   │
│  (io.o11y.kit:o11y-kit-spring-boot-starter)             │
│  一行依赖引入全部 o11y-kit 模块                            │
└──────────────────────────────────────────────────────────┘
```

## 关键状态

| 组件 | 状态 | 含义 |
|------|------|------|
| ServerObservationHandler | ENABLED / DISABLED | 是否拦截 Controller 请求 |
| ClientObservationHandler | APPLIED / NOT_APPLIED | 是否附加到 WebClient |
| TraceIdResolver | B3 / W3C / FALLBACK | 当前活跃的 traceId 解析策略 |
| MicrometerHttpMetricRecorder | ACTIVE / INACTIVE | 指标是否注册到 MeterRegistry |

## 权限边界

- **order-o11y（API 层）**：只依赖 `opentelemetry-api`（compile-only），无 Spring 依赖
- **order-adapter（Spring 实现层）**：依赖 `spring-webmvc` / `spring-webflux` / `micrometer-core`，这些在 order-demo 中已存在
- 用户需自行提供 `MeterRegistry` Bean 和 OpenTelemetry SDK 初始化
- `MicrometerHttpMetricRecorder` 不创建 MeterRegistry，从 Spring 容器获取
- `ClientObservationHandler` 不创建 Tracer，从 `TracerHelper.getTracer()` 获取

## 数据流

```
浏览器/客户端
  │
  ▼
┌─ ServerObservationHandler ──────────────┐
│  1. preHandle:                          │
│     - resolveTraceId(request.headers)   │
│     → X-B3-TraceId / traceparent / UUID │
│     - MDC.put("traceId", traceId)       │
│     - response.setHeader("X-Trace-Id")  │
│     - request.setAttribute(startTime)   │
│                                          │
│  2. afterCompletion:                    │
│     - duration = now - startTime        │
│     - recorder.recordServerRequest(     │
│         method, uri, status, duration)  │
│     - MDC.remove("traceId")             │
└──────────┬──────────────────────────────┘
           │ chain.doFilter() → Controller handler
           ▼
┌─ Controller ────────────────────────────┐
│  @PostMapping("/api/v1/orders")         │
│  → 返回 ResponseEntity                  │
└──────────┬──────────────────────────────┘
           │ WebClient 外部调用
           ▼
┌─ ClientObservationHandler ─────────────┐
│  1. filter(request, next):             │
│     - span = tracer.spanBuilder(name)  │
│     - tracedRequest = inject headers   │
│       (X-B3-TraceId, X-Trace-Id)       │
│     - startTime = now                  │
│                                         │
│  2. doOnNext(response):                │
│     - duration = now - startTime       │
│     - recorder.recordClientRequest(    │
│         method, host, status, duration)│
│                                         │
│  3. doOnError(error):                  │
│     - recorder.recordClientError(...)  │
│                                         │
│  4. doFinally: span.end()             │
└────────────────────────────────────────┘
```

## 暴露的 Metrics（Prometheus 格式）

| Metric 名称 | 类型 | Tags | 说明 |
|-------------|------|------|------|
| `o11y.server.requests` | Timer | `method`, `uri`, `status` (2xx/3xx/4xx/5xx) | Controller round-trip |
| `o11y.client.requests` | Timer | `method`, `host`, `status` (2xx/3xx/4xx/5xx) | WebClient 外部调用 |
| `o11y.client.errors` | Counter | `method`, `host`, `error` | WebClient 调用失败 |

## 验收标准

1. 编译期：`mvn clean compile` 通过，无新增依赖冲突
2. 单元测试：新增代码至少 80% 行覆盖率（与项目现有 JaCoCo 门禁一致）
3. 运行时验证：
   - Controller 请求后，`o11y.server.requests` Timer 在 Prometheus 端可见（可通过 `/actuator/prometheus` 验证）
   - 外部 WebClient 调用后，`o11y.client.requests` Timer 可见
   - 请求响应头部包含 `X-Trace-Id`
4. 向后兼容：现有 `TraceFilter` / `TraceAspect` / `OrderMetrics` / 各 Adapter 手动 span 保持可用（后续逐步迁移）
5. 抽取可行性：`order-o11y` 模块的 Java 文件零外部框架依赖，可直接复制到独立的 Maven 模块

## 仍不确定的问题

1. **开源包名与 groupId**：以 `io.o11y.kit` 发布还是 `io.github.observability-kit`？
2. **Spring Boot Starter 形式**：直接做成 `o11y-kit-spring-boot-starter` 自动配置，还是手动 `@Import` + 文档说明？
3. **WebClient Reactor 上下文**：`ExchangeFilterFunction` 中如何从 Reactor Context 正确传播 MDC traceId？目前 `doOnNext` 在 Reactor 线程执行，MDC 可能丢失
4. **旧代码迁移策略**：现有 `TraceAspect` / `TraceFilter` / `OrderMetrics` / 各 Adapter 中的手动 span 应保留 deprecated 标记还是立刻删除？
5. **Spring Boot 版本兼容**：最低支持 Boot 3.0（Jakarta）还是也向后兼容 Boot 2.x（javax）？
6. **是否支持 Spring Cloud Gateway**：如果目标是 Spring WebFlux 全覆盖，Gateway 场景的过滤器需要额外测试
7. **Maven 模块抽取时机**：在本仓库验证稳定后，通过 `mvn-jar` 发布到 Maven Central 还是 GitHub Packages？
