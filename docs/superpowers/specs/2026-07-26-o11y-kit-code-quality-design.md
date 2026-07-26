# o11y-kit 代码可读性可维护性提升设计文档

> 对 o11y-kit 进行深度重构，优化包结构、拆分大文件、统一 SPI 设计、合并自动配置。

**目标：** 提升 o11y-kit 的代码可读性、可维护性和代码结构，使其成为高质量、可扩展的通用观测 SDK。

**架构：** 在现有 3 模块结构基础上，优化包层级（移除 `micrometer` 包）、拆分职责过重的类（`O11yKitProperties`、`AbstractClientObservation`）、统一 SPI 家族（`HttpMetricRecorder` 接口→抽象类）、合并自动配置（WebMVC+WebFlux）、补充包级文档。

---

## 1. 包结构重组

### 当前结构

```
io.o11y.kit/
├── http/                    # HttpMetricRecorder, TraceIdResolver, O11yKitOrders
├── metrics/                 # BusinessMetricsPort, MicrometerMetricsAdapter
├── micrometer/              # MicrometerHttpMetricRecorder ← 与 metrics 职责重叠
├── spring/aop/              # Observed, ObservedAspect, ObservedAutoConfiguration
├── webmvc/                  # ServerObservationHandler
├── webmvc/client/           # 客户端观测拦截器
└── webflux/                 # ClientObservationHandler
```

### 目标结构

```
io.o11y.kit/
├── http/                    # HTTP 观测抽象（HttpMetricRecorder 抽象类, TraceIdResolver）
├── metrics/                 # 业务指标 SPI + 实现（合并 micrometer 包）
│   ├── BusinessMetricsPort.java
│   ├── MicrometerMetricsAdapter.java
│   └── MicrometerHttpMetricRecorder.java  ← 从 micrometer 移入
│
├── spring/
│   ├── aop/                 # @Observed 注解 + 切面
│   │   ├── Observed.java
│   │   ├── ObservedAspect.java
│   │   └── (ObservedAutoConfiguration 移出到 autoconfigure)
│   │
│   ├── webmvc/              # WebMVC 观测
│   │   ├── ServerObservationHandler.java
│   │   └── client/          # 客户端观测拦截器
│   │
│   └── webflux/             # WebFlux 观测
│
└── autoconfigure/           # 自动配置（starter 模块）
    ├── O11yKitProperties.java
    ├── ClientProperties.java
    ├── MetricsProperties.java
    ├── HttpMetricsAutoConfiguration.java
    ├── WebObservationAutoConfiguration.java  ← 合并 WebMVC + WebFlux
    ├── HttpClientObservationAutoConfiguration.java
    └── ObservedAutoConfiguration.java  ← 从 core 移入
```

### 关键变更

- `MicrometerHttpMetricRecorder` 从 `io.o11y.kit.micrometer` 移到 `io.o11y.kit.metrics`
- 删除 `io.o11y.kit.micrometer` 包
- `ObservedAutoConfiguration` 从 `io.o11y.kit.spring.aop` 移到 `io.o11y.kit.autoconfigure`
- `O11yKitOrders` 删除，常量合并到 `HttpClientObservationAutoConfiguration`

---

## 2. HttpMetricRecorder 接口 → 抽象类

### 当前（接口）

```java
public interface HttpMetricRecorder {
    void recordServerRequest(String method, String uri, int statusCode, long durationMs);
    void recordClientRequest(String method, String host, int statusCode, long durationMs);
    void recordClientError(String method, String host, String errorClass, long durationMs);
}
```

### 改为（抽象类）

```java
public abstract class HttpMetricRecorder {
    public void recordServerRequest(String method, String uri, int statusCode, long durationMs) {}
    public void recordClientRequest(String method, String host, int statusCode, long durationMs) {}
    public void recordClientError(String method, String host, String errorClass, long durationMs) {}
}
```

### 理由

- 用户只需 override 关心的方法，不需要实现所有三个
- 新增方法时不会破坏现有实现（默认空实现）
- 适合 SPI 演进，降低用户接入成本

### 影响范围

- `MicrometerHttpMetricRecorder`：`implements` → `extends`
- `ServerObservationHandler`、`AbstractClientObservation`：引用方式不变
- `HttpMetricsAutoConfiguration`：方法签名不变

---

## 3. 大文件拆分

### 3.1 O11yKitProperties（259行 → 3个文件）

| 文件 | 职责 |
|------|------|
| `O11yKitProperties.java` | 核心配置类，只保留 `@ConfigurationProperties` + 顶层字段 |
| `ClientProperties.java` | 客户端观测配置（原 `Client` 嵌套类） |
| `MetricsProperties.java` | 指标相关配置（原 `Client.Metrics` 嵌套类 + `ignorePatterns`） |

```java
// O11yKitProperties.java
@ConfigurationProperties(prefix = "o11y.kit")
public class O11yKitProperties {
    private ClientProperties client = new ClientProperties();
    public ClientProperties getClient() { return client; }
    public void setClient(ClientProperties client) { this.client = client; }
}
```

```java
// ClientProperties.java
public class ClientProperties {
    private boolean enabled = true;
    private MetricsProperties metrics = new MetricsProperties();
    private List<String> ignorePatterns = new ArrayList<>(List.of("/actuator/**", "/health/**"));
    // getters / setters
}
```

```java
// MetricsProperties.java
public class MetricsProperties {
    private boolean enabled = true;
    // getters / setters
}
```

### 3.2 AbstractClientObservation（265行 → 2个文件）

| 文件 | 职责 |
|------|------|
| `AbstractClientObservation.java` | 只保留指标记录逻辑（~150行） |
| `ClientSpanHandler.java` | 提取的 OpenTelemetry span 管理逻辑（~100行） |

```java
// ClientSpanHandler.java — 新增
public class ClientSpanHandler {
    private final Tracer tracer;
    private final boolean tracingEnabled;

    public ClientSpanHandler(Tracer tracer, boolean tracingEnabled) {
        this.tracer = tracer;
        this.tracingEnabled = tracingEnabled;
    }

    public Span beginSpan(HttpRequest request) {
        if (!tracingEnabled) return null;
        return tracer.spanBuilder(request.getMethod().name())
                .setAttribute("http.host", request.getURI().getHost())
                .startSpan();
    }

    public void endSpan(Span span, ClientHttpResponse response) {
        if (span == null) return;
        span.setAttribute("http.status_code", response.getStatusCode().value());
        span.end();
    }

    public void errorSpan(Span span, Throwable error) {
        if (span == null) return;
        span.recordException(error);
        span.end();
    }
}
```

---

## 4. 自动配置合并

### 当前（5个配置类）

| 类 | 位置 |
|----|------|
| `HttpMetricsAutoConfiguration` | starter/autoconfigure |
| `ObservationWebMvcAutoConfiguration` | starter/autoconfigure |
| `ObservationWebFluxAutoConfiguration` | starter/autoconfigure |
| `HttpClientObservationAutoConfiguration` | starter/autoconfigure |
| `ObservedAutoConfiguration` | core/spring/aop |

### 合并后（4个配置类，均在 starter/autoconfigure）

| 类 | 说明 |
|----|------|
| `HttpMetricsAutoConfiguration` | 不变 |
| `WebObservationAutoConfiguration` | 合并 WebMVC + WebFlux，用 `@ConditionalOnClass` 区分 |
| `HttpClientObservationAutoConfiguration` | 不变，常量 `O11yKitOrders` 移入 |
| `ObservedAutoConfiguration` | 从 core 移入 |

```java
// WebObservationAutoConfiguration.java
@AutoConfiguration(after = HttpMetricsAutoConfiguration.class)
@ConditionalOnWebApplication
public class WebObservationAutoConfiguration {

    @Configuration
    @ConditionalOnClass(DispatcherServlet.class)
    public static class WebMvcConfig {
        @Bean
        @ConditionalOnMissingBean
        public ServerObservationHandler serverObservationHandler(
                HttpMetricRecorder recorder, TraceIdResolver traceIdResolver) {
            return new ServerObservationHandler(recorder, traceIdResolver);
        }
    }

    @Configuration
    @ConditionalOnClass(org.springframework.web.reactive.DispatcherHandler.class)
    public static class WebFluxConfig {
        @Bean
        @ConditionalOnMissingBean
        public ClientObservationHandler clientObservationHandler(
                HttpMetricRecorder recorder, TraceIdResolver traceIdResolver) {
            return new ClientObservationHandler(recorder, traceIdResolver);
        }
    }
}
```

---

## 5. 常量合并

`O11yKitOrders.java` 删除，`CLIENT_OBSERVATION` 常量直接定义在使用它的 `HttpClientObservationAutoConfiguration` 中：

```java
// HttpClientObservationAutoConfiguration.java
@AutoConfiguration(after = HttpMetricsAutoConfiguration.class)
public class HttpClientObservationAutoConfiguration {
    static final int CLIENT_OBSERVATION_ORDER = Integer.MAX_VALUE - 100;
    // ...
}
```

---

## 6. 补充 package-info.java

为以下包添加包级 Javadoc：

| 包 | 文档内容 |
|----|---------|
| `io.o11y.kit.http` | HTTP 观测抽象，包含 `HttpMetricRecorder` 和 `TraceIdResolver` |
| `io.o11y.kit.metrics` | 业务指标 SPI 及 Micrometer 实现 |
| `io.o11y.kit.spring.webmvc` | Spring WebMVC 观测拦截器 |
| `io.o11y.kit.spring.webflux` | Spring WebFlux 观测处理器 |
| `io.o11y.kit.autoconfigure` | Spring Boot 自动配置 |

---

## 7. 实施顺序

| 步骤 | 内容 | 涉及文件 |
|------|------|---------|
| 1 | `HttpMetricRecorder` 接口→抽象类 | 1 个文件修改 + 1 个实现类调整 |
| 2 | 包结构重组：`MicrometerHttpMetricRecorder` 移到 `metrics` 包 | 2 个文件移动 + import 更新 |
| 3 | 拆分 `O11yKitProperties` 嵌套类 | 新建 2 个文件，修改 1 个 |
| 4 | 拆分 `AbstractClientObservation` → `ClientSpanHandler` | 新建 1 个文件，修改 1 个 |
| 5 | 合并 WebMVC + WebFlux 自动配置 | 新建 1 个文件，删除 2 个 |
| 6 | `ObservedAutoConfiguration` 移入 autoconfigure | 移动 1 个文件 + 更新 import |
| 7 | `O11yKitOrders` 合并到 `HttpClientObservationAutoConfiguration` | 删除 1 个，修改 1 个 |
| 8 | 补充 `package-info.java` | 新建 5 个文件 |
| 9 | 验证：编译 + 测试 | 全部模块 |