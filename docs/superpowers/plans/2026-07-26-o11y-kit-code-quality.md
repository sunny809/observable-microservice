# o11y-kit 代码质量提升 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提升 o11y-kit 的代码可读性、可维护性和代码结构。

**Architecture:** 包结构重组（移除 `micrometer` 包）、大文件拆分（`O11yKitProperties`、`AbstractClientObservation`）、SPI 统一（`HttpMetricRecorder` 接口→抽象类）、自动配置合并（WebMVC+WebFlux）、补充包级文档。

**Tech Stack:** Java 25, Spring Boot 3.4.3, Micrometer 1.14.5, OpenTelemetry 1.37.0

## Global Constraints

- 不修改现有 public API 方法签名（`HttpMetricRecorder` 从接口→抽象类，方法签名不变）
- 所有现有测试必须继续通过
- 每次提交后必须验证编译通过
- 遵循 Java 命名规范：常量 `UPPER_SNAKE_CASE`，类 `PascalCase`
- 包名使用全小写，单词间用点分隔

---

### Task 1: HttpMetricRecorder 接口 → 抽象类

**Files:**
- Modify: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/HttpMetricRecorder.java`
- Modify: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorder.java`

**Interfaces:**
- Consumes: 无
- Produces: `HttpMetricRecorder` 抽象类（方法签名不变，添加默认空实现）

- [ ] **Step 1: 修改 HttpMetricRecorder.java**

```java
package io.o11y.kit.http;

/**
 * Abstract base for recording HTTP request metrics for both server-side
 * and client-side calls. All methods have empty default implementations
 * so subclasses only need to override the methods they care about.
 *
 * <p>Server-side calls represent inbound requests handled by a Controller.
 * Client-side calls represent outbound requests made via HTTP clients.
 *
 * @since 0.1.0
 */
public abstract class HttpMetricRecorder {

    /**
     * Records a completed server-side (inbound) HTTP request.
     */
    public void recordServerRequest(String method, String uri, int statusCode, long durationMs) {}

    /**
     * Records a completed client-side (outbound) HTTP request.
     */
    public void recordClientRequest(String method, String host, int statusCode, long durationMs) {}

    /**
     * Records a failed client-side request (connection error, timeout, etc.).
     */
    public void recordClientError(String method, String host, String errorClass, long durationMs) {}
}
```

- [ ] **Step 2: 更新 MicrometerHttpMetricRecorder**

将 `implements HttpMetricRecorder` 改为 `extends HttpMetricRecorder`，保留所有 `@Override` 注解和方法体不变。

- [ ] **Step 3: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-core -am -Djacoco.skip=true -q
```

Expected: 编译成功

- [ ] **Step 4: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: 67 tests, 0 failures

- [ ] **Step 5: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): change HttpMetricRecorder from interface to abstract class"
```

---

### Task 2: 包结构重组 — 移动 MicrometerHttpMetricRecorder

**Files:**
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/MicrometerHttpMetricRecorder.java` (复制到新位置)
- Delete: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorder.java` (删除旧位置)
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/metrics/MicrometerHttpMetricRecorderTest.java` (复制到新位置)
- Delete: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorderTest.java` (删除旧位置)
- Delete: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/` (空目录)
- Delete: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/` (空目录)

**Interfaces:**
- Consumes: Task 1 产出的 `HttpMetricRecorder` 抽象类
- Produces: 统一的 `io.o11y.kit.metrics` 包

- [ ] **Step 1: 复制文件到新位置**

```bash
cp o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorder.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/
cp o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorderTest.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/metrics/
```

- [ ] **Step 2: 更新 package 声明**

将新文件中的 `package io.o11y.kit.micrometer` 改为 `package io.o11y.kit.metrics`。

- [ ] **Step 3: 更新 import 语句**

检查所有引用 `io.o11y.kit.micrometer.MicrometerHttpMetricRecorder` 的地方，更新为 `io.o11y.kit.metrics.MicrometerHttpMetricRecorder`。

```bash
grep -rn "io.o11y.kit.micrometer" o11y-kit/ --include="*.java" --include="*.imports" --include="*.factories"
```

- [ ] **Step 4: 删除旧文件**

```bash
rm o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorder.java
rm o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorderTest.java
rmdir o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer
rmdir o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer
```

- [ ] **Step 5: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-core -am -Djacoco.skip=true -q
```

Expected: 编译成功

- [ ] **Step 6: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): move MicrometerHttpMetricRecorder to metrics package"
```

---

### Task 3: 拆分 O11yKitProperties 嵌套类

**Files:**
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ClientProperties.java`
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/MetricsProperties.java`
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/O11yKitProperties.java`

**Interfaces:**
- Consumes: 无
- Produces: 3 个独立的配置属性类

- [ ] **Step 1: 创建 ClientProperties.java**

```java
package io.o11y.kit.autoconfigure;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for outbound HTTP client observability.
 *
 * <p>Bound under {@code o11y.kit.client}.
 */
public class ClientProperties {

    /** Whether client-side HTTP observability is enabled. */
    private boolean enabled = true;

    /** Metrics recording configuration. */
    private MetricsProperties metrics = new MetricsProperties();

    /** URL patterns to ignore (excluded from observation). */
    private List<String> ignorePatterns = new ArrayList<>(List.of(
            "/actuator/**", "/health/**", "/info/**", "/prometheus/**"
    ));

    // getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }
    public List<String> getIgnorePatterns() { return ignorePatterns; }
    public void setIgnorePatterns(List<String> ignorePatterns) { this.ignorePatterns = ignorePatterns; }
}
```

- [ ] **Step 2: 创建 MetricsProperties.java**

```java
package io.o11y.kit.autoconfigure;

/**
 * Configuration properties for metrics recording.
 *
 * <p>Bound under {@code o11y.kit.client.metrics}.
 */
public class MetricsProperties {

    /** Whether metrics recording is enabled. */
    private boolean enabled = true;

    // getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

- [ ] **Step 3: 简化 O11yKitProperties.java**

删除 `Client`、`Metrics` 嵌套类，改为引用独立的 `ClientProperties` 和 `MetricsProperties`：

```java
@ConfigurationProperties(prefix = "o11y.kit")
public class O11yKitProperties {

    @NestedConfigurationProperty
    private ClientProperties client = new ClientProperties();

    public ClientProperties getClient() { return client; }
    public void setClient(ClientProperties client) { this.client = client; }
}
```

- [ ] **Step 4: 更新所有引用嵌套类的代码**

搜索 `O11yKitProperties.Client` 和 `O11yKitProperties.Metrics`，改为 `ClientProperties` 和 `MetricsProperties`。

- [ ] **Step 5: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 6: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): split O11yKitProperties nested classes into separate files"
```

---

### Task 4: 拆分 AbstractClientObservation — 提取 ClientSpanHandler

**Files:**
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/ClientSpanHandler.java`
- Modify: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/AbstractClientObservation.java`

**Interfaces:**
- Consumes: OpenTelemetry `Tracer`
- Produces: `ClientSpanHandler` 类，专注 span 生命周期管理

- [ ] **Step 1: 创建 ClientSpanHandler.java**

```java
package io.o11y.kit.webmvc.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Manages OpenTelemetry span lifecycle for client-side HTTP observations.
 *
 * <p>Handles span creation, attribute recording, exception recording, and
 * span finalization. Separated from {@link AbstractClientObservation} to
 * keep metric-recording and span-management concerns in distinct classes.
 */
public class ClientSpanHandler {

    private final Tracer tracer;
    private final boolean tracingEnabled;

    public ClientSpanHandler(Tracer tracer, boolean tracingEnabled) {
        this.tracer = tracer;
        this.tracingEnabled = tracingEnabled;
    }

    /** Start a new span for the given request, or return null if tracing is disabled. */
    public Span beginSpan(HttpRequest request) {
        if (!tracingEnabled || tracer == null) return null;
        return tracer.spanBuilder(request.getMethod().name())
                .setAttribute("http.host", request.getURI().getHost())
                .setAttribute("http.method", request.getMethod().name())
                .startSpan();
    }

    /** End the span on successful response. */
    public void endSpan(Span span, ClientHttpResponse response) {
        if (span == null) return;
        try {
            span.setAttribute("http.status_code", response.getStatusCode().value());
        } catch (Exception ignored) {
            span.setAttribute("http.status_code", 0);
        }
        span.end();
    }

    /** End the span on error. */
    public void errorSpan(Span span, Throwable error) {
        if (span == null) return;
        span.recordException(error);
        span.end();
    }
}
```

- [ ] **Step 2: 修改 AbstractClientObservation**

将 span 相关逻辑替换为 `ClientSpanHandler` 委托调用：

```java
// 在 AbstractClientObservation 中，添加字段：
protected final ClientSpanHandler spanHandler;

// 修改构造函数，添加 ClientSpanHandler 参数
protected AbstractClientObservation(HttpMetricRecorder recorder,
                                     TraceIdResolver traceIdResolver,
                                     ClientSpanHandler spanHandler) {
    this.recorder = recorder;
    this.traceIdResolver = traceIdResolver;
    this.spanHandler = spanHandler;
}

// 将原有的 span 创建/结束逻辑替换为 spanHandler 调用
// begin() 方法中：
// 替换前: Span span = tracer.spanBuilder(...)
// 替换后: Span span = spanHandler.beginSpan(request)
```

- [ ] **Step 3: 更新子类构造函数**

更新 `RestTemplateObservationInterceptor` 和 `RestClientObservationInterceptor` 的构造函数，传入 `ClientSpanHandler`。

- [ ] **Step 4: 更新自动配置**

在 `HttpClientObservationAutoConfiguration` 中添加 `ClientSpanHandler` bean：

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(name = "o11y.kit.client.enabled", havingValue = "true", matchIfMissing = true)
public ClientSpanHandler clientSpanHandler(Tracer tracer) {
    return new ClientSpanHandler(tracer, true);
}
```

- [ ] **Step 5: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 6: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): extract ClientSpanHandler from AbstractClientObservation"
```

---

### Task 5: 合并 WebMVC + WebFlux 自动配置

**Files:**
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/WebObservationAutoConfiguration.java`
- Delete: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebMvcAutoConfiguration.java`
- Delete: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebFluxAutoConfiguration.java`
- Delete: 对应的 test 文件（如果存在）
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Consumes: `HttpMetricRecorder`, `TraceIdResolver`
- Produces: 合并后的 `WebObservationAutoConfiguration`

- [ ] **Step 1: 创建 WebObservationAutoConfiguration.java**

```java
package io.o11y.kit.autoconfigure;

import io.o11y.kit.http.HttpMetricRecorder;
import io.o11y.kit.http.TraceIdResolver;
import io.o11y.kit.webflux.ClientObservationHandler;
import io.o11y.kit.webmvc.ServerObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.servlet.DispatcherServlet;

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
    @ConditionalOnClass(DispatcherHandler.class)
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

- [ ] **Step 2: 更新 AutoConfiguration.imports**

将 `io.o11y.kit.autoconfigure.ObservationWebMvcAutoConfiguration` 和 `io.o11y.kit.autoconfigure.ObservationWebFluxAutoConfiguration` 替换为 `io.o11y.kit.autoconfigure.WebObservationAutoConfiguration`。

- [ ] **Step 3: 删除旧文件**

```bash
rm o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebMvcAutoConfiguration.java
rm o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebFluxAutoConfiguration.java
```

- [ ] **Step 4: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 5: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 6: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): merge WebMVC and WebFlux auto-configuration"
```

---

### Task 6: ObservedAutoConfiguration 移入 autoconfigure 包

**Files:**
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservedAutoConfiguration.java`
- Delete: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/ObservedAutoConfiguration.java`

**Interfaces:**
- Consumes: 无
- Produces: 统一在 autoconfigure 包中的自动配置

- [ ] **Step 1: 复制文件**

```bash
cp o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/ObservedAutoConfiguration.java o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/
```

- [ ] **Step 2: 更新 package 声明**

将新文件中的 `package io.o11y.kit.spring.aop` 改为 `package io.o11y.kit.autoconfigure`。

- [ ] **Step 3: 更新 AutoConfiguration.imports**

将 `io.o11y.kit.spring.aop.ObservedAutoConfiguration` 改为 `io.o11y.kit.autoconfigure.ObservedAutoConfiguration`。

- [ ] **Step 4: 删除旧文件**

```bash
rm o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/ObservedAutoConfiguration.java
```

- [ ] **Step 5: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 6: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): move ObservedAutoConfiguration to autoconfigure package"
```

---

### Task 7: O11yKitOrders 合并到 HttpClientObservationAutoConfiguration

**Files:**
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/HttpClientObservationAutoConfiguration.java`
- Delete: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/O11yKitOrders.java`

**Interfaces:**
- Consumes: 无
- Produces: 删除 `O11yKitOrders`，常量移到使用处

- [ ] **Step 1: 搜索 O11yKitOrders 的所有引用**

```bash
grep -rn "O11yKitOrders" o11y-kit/ --include="*.java"
```

- [ ] **Step 2: 将常量移入 HttpClientObservationAutoConfiguration**

```java
// 在 HttpClientObservationAutoConfiguration 中添加
static final int CLIENT_OBSERVATION_ORDER = Integer.MAX_VALUE - 100;
```

- [ ] **Step 3: 更新所有引用**

将所有 `O11yKitOrders.CLIENT_OBSERVATION` 替换为 `HttpClientObservationAutoConfiguration.CLIENT_OBSERVATION_ORDER`。

- [ ] **Step 4: 删除 O11yKitOrders.java**

```bash
rm o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/O11yKitOrders.java
```

- [ ] **Step 5: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 6: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core,o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "refactor(o11y-kit): merge O11yKitOrders constants into HttpClientObservationAutoConfiguration"
```

---

### Task 8: 补充 package-info.java

**Files:**
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/package-info.java`
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/package-info.java` (已存在，更新)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/webmvc/package-info.java`
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/webflux/package-info.java`
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/package-info.java`

- [ ] **Step 1: 创建 http/package-info.java**

```java
/**
 * HTTP observability abstractions.
 *
 * <p>Contains the {@link io.o11y.kit.http.HttpMetricRecorder} abstract class
 * for recording HTTP request metrics, and {@link io.o11y.kit.http.TraceIdResolver}
 * for extracting trace IDs from incoming HTTP headers.
 *
 * @since 0.1.0
 */
package io.o11y.kit.http;
```

- [ ] **Step 2: 创建 webmvc/package-info.java**

```java
/**
 * Spring WebMVC integration for server-side HTTP observation.
 *
 * <p>Contains {@link io.o11y.kit.webmvc.ServerObservationHandler} and
 * client-side interceptors for RestTemplate and RestClient.
 *
 * @since 0.1.0
 */
package io.o11y.kit.webmvc;
```

- [ ] **Step 3: 创建 webflux/package-info.java**

```java
/**
 * Spring WebFlux integration for client-side HTTP observation.
 *
 * <p>Contains {@link io.o11y.kit.webflux.ClientObservationHandler} for
 * observing WebClient requests via ExchangeFilterFunction.
 *
 * @since 0.1.0
 */
package io.o11y.kit.webflux;
```

- [ ] **Step 4: 创建 autoconfigure/package-info.java**

```java
/**
 * Spring Boot auto-configuration for o11y-kit modules.
 *
 * <p>Auto-configuration classes are registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * @since 0.2.0-alpha
 */
package io.o11y.kit.autoconfigure;
```

- [ ] **Step 5: 更新 metrics/package-info.java**

更新为描述业务指标 SPI 和实现。

- [ ] **Step 6: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-core,o11y-kit-spring-boot-starter -am -Djacoco.skip=true -q
```

- [ ] **Step 7: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo && git add o11y-kit/ && git commit -m "docs(o11y-kit): add package-info.java for all packages"
```

---

### Task 9: 全量验证

**Files:**
- 无新增文件

- [ ] **Step 1: 编译所有模块**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn compile -pl o11y-kit-core,o11y-kit-spring-boot-starter,o11y-kit-test -am -Djacoco.skip=true -q
```

- [ ] **Step 2: 运行所有测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 3: 验证 order-demo 编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo && mvn compile -pl order-application,order-adapter -am -Dnet.bytebuddy.experimental=true -Djacoco.skip=true -q
```

- [ ] **Step 4: 终端确认**

```bash
echo "✅ o11y-kit-core: $(cd /home/bjdeng/project/java_projects/order-demo/o11y-kit && mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep 'Tests run:')"
echo "✅ order-application: $(cd /home/bjdeng/project/java_projects/order-demo && mvn test -pl order-application -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep 'Tests run:')"
```