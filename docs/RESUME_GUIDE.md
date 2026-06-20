# 订单履约微服务 — 简历项目介绍策略

## 项目核心亮点（按技术维度盘点）

### 🏛️ 架构设计
- **六边形架构（Hexagonal/Ports & Adapters）**：5 个 Maven 模块严格分层，domain 层零框架依赖
- **ArchUnit 编译期约束**：8+ 条架构规则在 CI 阶段强制执行（领域层不能依赖适配器、Controller 必须在指定包、JPA 实体禁止泄漏到 service 层等）
- **DDD 战术建模**：聚合根（Order）、值对象（InventoryReservation）、领域事件（WmsInstructionRequiredEvent 等）、领域异常

### 🔄 分布式事务
- **Saga 编排模式**：协调 4 个外部服务（Inventory / Order DB / WMS / TMS），包含完整补偿逻辑
- **异步事件驱动**：`@TransactionalEventListener(AFTER_COMMIT)` + `CompletableFuture` 链式编排 + `TransactionTemplate` 处理 Reactor 线程无 Spring 事务的问题
- **状态机管理**：Order 状态机 7 个状态（CREATED → WMS_ACKED → WMS_PICKED → TMS_DISPATCHED + REJECTED/TMS_REJECTED 失败分支）

### 🛡️ 弹性与容错
- **Resilience4j 全套**：每个外部调用都有 `@CircuitBreaker` + `@Retry`（指数退避）+ `@TimeLimiter`，配置 `@RateLimiter`（100 req/min）
- **双层幂等性**：Caffeine 本地缓存（30 min TTL，sub-ms 命中）+ 数据库 unique constraint（强一致性兜底）
- **优雅降级**：每个外部调用都有 fallback 方法

### 📊 可观测性（Three Pillars）
- **Tracing**：OpenTelemetry + Jaeger，自定义 `@Traced` AOP 注解，支持 B3 和 W3C `traceparent` 头透传
- **Metrics**：Micrometer + Prometheus，自定义业务指标（orders.placed、saga.duration），SLO 直方图
- **Logging**：JSON 结构化日志（Logstash Logback encoder），traceId/spanId 自动注入

### ☁️ 云原生部署
- **Kubernetes 生产级清单**：Deployment、Service、HPA（CPU 70% / 内存 80%，scale-up 50%/60s、scale-down 10%/60s + 300s 稳定窗口）、ConfigMap、Secret
- **Helm Chart**：可配置 replica、resources、autoscaling，podSecurityContext（runAsNonRoot、readOnlyRootFilesystem、drop ALL caps）
- **Graceful Shutdown**：preStop 10s sleep + 30s terminationGracePeriod
- **Docker 多阶段构建**：依赖缓存优化，非 root 用户运行，包含 HEALTHCHECK

### 🧪 测试金字塔（完整）
- **单元测试**：JUnit 5 + Mockito（87+ 测试）
- **集成测试**：JPA Repository 测试 with H2
- **架构测试**：ArchUnit（8 条规则）
- **BDD 测试**：Cucumber + WireMock + Awaitility，7 个 feature 文件 / 16 个场景
- **JaCoCo 覆盖率**：80% 阈值

### 🚀 CI/CD 与安全
- **GitHub Actions**：完整流水线（build → test → coverage → OWASP 依赖扫描 → Docker 构建 → Trivy 容器扫描 → SARIF 上传到 CodeQL）
- **OAuth2 Resource Server**：JWT 验证 + `@PreAuthorize("hasAuthority('SCOPE_order:write')")`
- **多 Profile 隔离**：production（JWT 强校验）、local（关闭安全）、test（BDD 旁路）

### 💻 技术栈
Java 21 / Spring Boot 3.4.3 / Spring Cloud 2023.0.6 / PostgreSQL 16 / Kafka / Flyway / Resilience4j / OpenTelemetry / Caffeine / Cucumber / WireMock / ArchUnit / K8s / Helm / Docker

---

## 三种简历表述风格（按目标岗位选用）

### 📝 风格 A：后端工程师岗位（突出业务复杂度 + 设计模式）

> **订单服务系统（Spring Boot 微服务）** | Personal Project | 2025
>
> 基于 **Java 21 / Spring Boot 3.4** 实现的生产级订单履约服务，模拟电商订单从下单到发运的完整链路（库存预占 → 订单落库 → WMS 拣货 → TMS 派车），强调**领域驱动设计**和**分布式事务一致性**。
>
> - **六边形架构 + 5 模块分层**：领域层零框架依赖，通过 ArchUnit 在 CI 中强制执行 8 条架构边界规则，杜绝跨层依赖泄漏
> - **Saga 编排实现分布式事务**：协调 4 个外部服务，使用 `@TransactionalEventListener(AFTER_COMMIT)` + `CompletableFuture` 链式异步编排，配套完整补偿逻辑，订单状态机覆盖 7 种状态包括失败分支
> - **双层幂等设计**：Caffeine 本地缓存（30min TTL，sub-ms 命中）+ 数据库 unique 约束兜底，解决重复下单问题
> - **Resilience4j 全栈容错**：每个外部 HTTP 调用配置熔断（50% 失败率阈值）、重试（指数退避）、超时（10s）、限流（100 req/min），所有调用提供 fallback
> - **测试金字塔**：87+ 单元测试 + 16 个 Cucumber BDD 场景（WireMock 模拟外部依赖 + Awaitility 异步断言）+ ArchUnit 架构测试，JaCoCo 覆盖率 80%
>
> **技术栈**：Java 21、Spring Boot 3.4、PostgreSQL、Kafka、Resilience4j、OpenTelemetry、Cucumber、ArchUnit

---

### 📝 风格 B：架构师/资深工程师岗位（突出架构决策 + 权衡思考）

> **订单履约平台（云原生微服务）** | Personal Project | 2025
>
> 设计并实现的生产级订单服务，目标是**演示企业级微服务架构中的关键决策点**——包括分布式事务的一致性权衡、可观测性的工程实现、以及云原生部署的最佳实践。
>
> - **架构决策**：采用六边形架构 + DDD 战术建模，撰写 6 份 ADR（Architecture Decision Records）记录关键技术选型，包括 Saga 异步编排的事务管理方案（Reactor Netty 线程上下文丢失问题）、本地缓存幂等性的可扩展性局限、以及 OpenTelemetry span 生命周期与异步调用的边界问题
> - **分布式事务**：基于 Saga 编排模式实现订单履约链路（库存 → 订单 → WMS → TMS），通过 Spring `@TransactionalEventListener(AFTER_COMMIT)` 解耦同步主流程与异步后续步骤，使用 `CompletableFuture.thenCompose()` 实现补偿链
> - **可观测性体系（Three Pillars）**：OpenTelemetry 分布式追踪（自定义 `@Traced` AOP 注解 + B3/W3C `traceparent` 透传）、Micrometer + Prometheus 指标（业务自定义指标 + SLO 直方图）、JSON 结构化日志（traceId 注入）
> - **云原生部署**：Kubernetes（Deployment + HPA 双指标弹性策略 + 优雅停机 30s + 非 root 安全上下文）+ Helm Chart（生产级 values 配置）+ 多阶段 Docker 构建（Trivy CI 扫描）
> - **CI/CD 与质量保障**：GitHub Actions 流水线集成 OWASP 依赖扫描、Trivy 容器扫描、JaCoCo 覆盖率门禁（80%）、ArchUnit 架构规则强制
>
> **关键技术**：Hexagonal Architecture、Saga Pattern、Resilience4j、OpenTelemetry、Kubernetes、Helm、ArchUnit

---

### 📝 风格 C：DevOps / Platform / SRE 岗位（突出可观测性 + 部署）

> **生产级订单服务（DevOps 工程演示）** | Personal Project | 2025
>
> 在一个 Spring Boot 订单微服务上完整实现了**从代码到 K8s 生产部署**的工程闭环，重点演示**可观测性、CI/CD 安全扫描、容器化最佳实践**。
>
> - **完整 CI/CD 流水线**（GitHub Actions）：Maven 构建 → 单元/集成/BDD 测试 → JaCoCo 覆盖率（80% 门禁）→ OWASP 依赖漏洞扫描 → Docker 多阶段构建 → Trivy 容器扫描（CRITICAL/HIGH）→ SARIF 上传 GitHub CodeQL
> - **Kubernetes 生产级部署**：Deployment（双副本 + 资源 requests/limits）+ HPA（CPU 70% + 内存 80% 双指标，scale-up 50%/60s 激进、scale-down 10%/60s 保守 + 300s 稳定窗口）+ preStop hook（10s sleep）+ 30s terminationGracePeriod 实现优雅停机
> - **安全加固**：Pod 安全上下文（runAsNonRoot + readOnlyRootFilesystem + drop ALL capabilities）、Docker 非 root 用户运行、OAuth2 JWT 资源服务器认证
> - **三支柱可观测性**：OpenTelemetry 分布式追踪（OTLP gRPC → Jaeger）+ Micrometer Prometheus 指标（含 SLO 直方图 `http: 50ms,100ms,200ms,500ms,1s,2s,5s`）+ JSON 结构化日志（Logstash encoder + traceId 自动关联）
> - **Helm Chart**：参数化 replica/resources/autoscaling/probes 配置，便于多环境部署
> - **Docker 镜像优化**：多阶段构建（builder + JRE Alpine 运行时）、依赖层缓存、HEALTHCHECK 指令
>
> **技术栈**：Kubernetes、Helm、Docker、GitHub Actions、Trivy、OWASP、OpenTelemetry、Prometheus、Jaeger、Spring Boot

---

## 简历介绍的"加分项"（建议都加上）

| 类别 | 内容 | 为什么加分 |
|------|------|----------|
| **可量化指标** | 87 单元测试 / 16 BDD 场景 / 80% 覆盖率 / 5 个 Maven 模块 / 8 条 ArchUnit 规则 | 显示规模感和工程严谨 |
| **GitHub 链接** | 仓库链接 + README 中的 mermaid 架构图 | 让面试官 5 秒看懂架构 |
| **ADR 文档** | 提及"撰写了 X 份架构决策记录" | 显示架构思维和文档习惯 |
| **诚实文档** | README 中记录的 6 项 Known Issues | 反而显示成熟（不是吹嘘） |
| **CI 徽章** | README 顶部的 build / coverage / license 徽章 | 视觉上立刻显得专业 |

---

## 面试时可以深入聊的"故事点"（准备 STAR 回答）

按"面试官最爱问的细节"排序：

### ⭐ Story 1：分布式事务一致性踩坑
- **Situation**：异步 Saga 中 WMS 回调需要更新订单状态
- **Task**：在 Reactor Netty 线程中执行 JPA 操作
- **Action**：发现 `@Transactional` 失效（线程无 Spring 上下文）→ 改用 `TransactionTemplate.executeWithoutResult()` 显式管理事务边界
- **Result**：解决了异步回调中的事务一致性问题，写在 ADR-1 中

### ⭐ Story 2：幂等性方案的权衡
- **Situation**：防止重复下单
- **Task**：在性能（毫秒级响应）和正确性（多实例一致）之间权衡
- **Action**：选择 Caffeine 本地缓存（快路径）+ 数据库 unique constraint（兜底）的双层方案，明确记录"多实例下缓存不一致"的局限性
- **Result**：单实例下 sub-ms 命中，多实例下数据库兜底保证最终正确性

### ⭐ Story 3：架构边界强制执行
- **Situation**：长期维护中，跨层依赖容易悄悄出现
- **Task**：在编译期发现违规
- **Action**：引入 ArchUnit 编写 8 条规则（领域层不能引用框架、Controller 必须在指定包等），集成到 CI 流水线
- **Result**：任何架构违规都会让 PR 失败，无需依赖人工 code review

### ⭐ Story 4：BDD 测试的实践
- **Situation**：业务流程涉及 4 个外部服务，单元测试无法覆盖端到端逻辑
- **Task**：构建可执行的业务规约
- **Action**：使用 Cucumber + WireMock 模拟外部服务 + Awaitility 处理异步断言，16 个场景覆盖正常路径 + 异常路径 + 补偿路径
- **Result**：业务规约即测试，新人理解业务直接读 feature 文件

### ⭐ Story 5：可观测性闭环
- **Situation**：分布式系统排障困难
- **Task**：实现 traces / metrics / logs 三者关联
- **Action**：OpenTelemetry 自定义 `@Traced` AOP 注解 + 自动注入 traceId 到 MDC + Logback JSON encoder 输出 → Jaeger 看链路、Prometheus 看指标、Kibana 按 traceId 拉日志
- **Result**：一个 traceId 串联所有信号，排障时间从分钟级降到秒级

---

## 不建议的写法（避坑）

- ❌ "学习了 Spring Boot 和微服务" → 太初级
- ❌ "实现了订单功能" → 没有任何技术深度信息
- ❌ 罗列一长串技术栈但不解释**为什么用** → 显得堆砌
- ❌ 写"高并发"、"高可用"但没有任何指标支撑 → 显得虚
- ❌ 不放 GitHub 链接 → 面试官无法验证

---

## 推荐的简历呈现格式（即拷即用）

```
订单履约微服务 | Personal Project | github.com/your-name/order-demo

【一句话定位】基于六边形架构和 Saga 模式的生产级 Spring Boot 订单服务，
完整实现分布式事务、弹性容错、可观测性和云原生部署。

【核心亮点】
• 六边形架构 + ArchUnit 编译期边界约束（5 模块/8 条规则）
• Saga 编排实现订单履约链路，覆盖 4 个外部服务的补偿逻辑
• 双层幂等 + Resilience4j 全栈容错（熔断/重试/限流/超时）
• OpenTelemetry + Prometheus + JSON 日志三支柱可观测性
• K8s 生产级部署（HPA 双指标 / 优雅停机 / 安全上下文）+ Helm Chart
• GitHub Actions CI/CD（含 OWASP + Trivy 双重安全扫描）
• 测试金字塔：87 单测 + 16 Cucumber BDD + ArchUnit，覆盖率 80%

【技术栈】Java 21、Spring Boot 3.4、PostgreSQL、Kafka、Resilience4j、
OpenTelemetry、Kubernetes、Helm、Docker、GitHub Actions
```

---

## 验证清单（提交简历前自查）

- [ ] 是否包含 GitHub 链接？
- [ ] 是否有具体数字（测试数、模块数、覆盖率等）？
- [ ] 是否针对目标岗位选择了对应风格（A/B/C）？
- [ ] 是否准备好了 3-5 个 STAR 故事点应对深挖？
- [ ] README 是否有架构图、徽章、清晰的目录？
- [ ] 是否能在 60 秒内口头讲清楚项目"做了什么 + 为什么这么做 + 解决了什么问题"？

---

## 下一步建议（让这个项目"更香"，可选）

> 这些不做也完全够用，但做了能在面试中讲出更多故事：

1. **加性能压测报告**（JMeter / k6 跑一轮，README 里贴 P50/P95/P99 + QPS）
2. **加 Grafana Dashboard 截图**（让可观测性可视化）
3. **写一篇博客**（掘金/Medium），讲一个深度技术点（比如 Saga 的事务管理），简历里贴链接
4. **录一个 demo 视频**（5 分钟讲清架构 + 跑一遍流程），简历里附 YouTube/B 站链接
5. **加压测对比**（开启 vs 关闭熔断的 QPS 对比），显示弹性的实际效果

---

## 一句话总结

这个项目最值钱的不是"用了多少技术"，而是**展示了你对生产级工程的理解**——包括架构边界、可观测性、弹性、安全、部署、测试金字塔等**真正工业级实践**。

按目标岗位选择风格 A/B/C，配 GitHub 链接 + 量化指标 + STAR 故事点，足以在简历筛选和面试中脱颖而出。
