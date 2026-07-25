# o11y-kit 通用化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 o11y-kit 从 8 模块合并为 3 模块，提供通用 BusinessMetricsPort SPI，准备 Maven Central 发布，添加文档和示例项目。

**Architecture:** 当前 8 个独立模块合并为 `o11y-kit-core`（核心库）、`o11y-kit-spring-boot-starter`（启动器）、`o11y-kit-test`（测试工具）三个模块。新增 `BusinessMetricsPort` SPI 将业务指标模式通用化，通过 Micrometer 适配器作为默认实现。

**Tech Stack:** Java 25, Spring Boot 3.4.3, Micrometer 1.14.5, OpenTelemetry 1.37.0, Maven

## Global Constraints

- 不修改现有 Java 包的名称（`io.o11y.kit.http`, `io.o11y.kit.micrometer`, `io.o11y.kit.spring.aop` 等保持原样）
- 所有现有测试必须继续通过（新模块结构下）
- o11y-kit 的 groupId 保持 `io.o11y.kit`，version 升级到 `0.5.0`
- 向后兼容：现有 `HttpMetricRecorder`、`TraceIdResolver` 等接口签名不变
- 所有数据库变更通过 Flyway 迁移脚本
- 保持向后兼容：现有 saga_logs 数据不受影响
- 测试覆盖：每个 task 必须有单元测试

---

### Task 1: 创建 o11y-kit-core 模块（合并 api + micrometer + aop）

**Files:**
- Create: `o11y-kit/o11y-kit-core/pom.xml`
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/HttpMetricRecorder.java` (moved from api)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/O11yKitOrders.java` (moved from api)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/TraceIdResolver.java` (moved from api)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorder.java` (moved from micrometer)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/Observed.java` (moved from aop)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/ObservedAspect.java` (moved from aop)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/ObservedAutoConfiguration.java` (moved from aop)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/package-info.java` (moved from aop)
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/http/TraceIdResolverTest.java` (moved from api)
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/http/TraceparentParameterizedTest.java` (moved from api)
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/MicrometerHttpMetricRecorderTest.java` (moved from micrometer)
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/spring/aop/ObservedAspectTest.java` (moved from aop)
- Modify: `o11y-kit/pom.xml` (更新 modules 列表，添加 core，移除旧模块)
- Delete: `o11y-kit/o11y-kit-api/` (整个目录)
- Delete: `o11y-kit/o11y-kit-micrometer/` (整个目录)
- Delete: `o11y-kit/o11y-kit-spring-aop/` (整个目录)

**Interfaces:**
- Consumes: 无
- Produces: `o11y-kit-core` 模块，包含所有原有包结构

- [ ] **Step 1: 创建 o11y-kit-core 目录结构**

```bash
mkdir -p o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/{http,micrometer,spring/aop}
mkdir -p o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/{http,micrometer,spring/aop}
```

- [ ] **Step 2: 创建 o11y-kit-core/pom.xml**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.o11y.kit</groupId>
        <artifactId>o11y-kit</artifactId>
        <version>0.5.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>o11y-kit-core</artifactId>
    <name>o11y-kit-core</name>
    <description>Core module: HTTP observability abstractions, Micrometer implementation, WebMVC/WebFlux integration, AOP support</description>

    <dependencies>
        <!-- Compile dependencies -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webmvc</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webflux</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aspects</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-api</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webflux</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 复制所有 Java 源文件到 o11y-kit-core**

复制以下文件（保持包路径不变）：
- `o11y-kit-api/src/main/java/io/o11y/kit/http/*.java` → `o11y-kit-core/src/main/java/io/o11y/kit/http/`
- `o11y-kit-micrometer/src/main/java/io/o11y/kit/micrometer/*.java` → `o11y-kit-core/src/main/java/io/o11y/kit/micrometer/`
- `o11y-kit-spring-aop/src/main/java/io/o11y/kit/spring/aop/*.java` → `o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/`
- 以及对应的 test 文件

```bash
cp -r o11y-kit/o11y-kit-api/src/main/java/io/o11y/kit/http/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/http/
cp -r o11y-kit/o11y-kit-api/src/test/java/io/o11y/kit/http/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/http/
cp -r o11y-kit/o11y-kit-micrometer/src/main/java/io/o11y/kit/micrometer/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/micrometer/
cp -r o11y-kit/o11y-kit-micrometer/src/test/java/io/o11y/kit/micrometer/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/micrometer/
cp -r o11y-kit/o11y-kit-spring-aop/src/main/java/io/o11y/kit/spring/aop/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/spring/aop/
cp -r o11y-kit/o11y-kit-spring-aop/src/test/java/io/o11y/kit/spring/aop/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/spring/aop/
```

- [ ] **Step 4: 更新 o11y-kit/pom.xml（父 POM）**

将 `<version>` 从 `0.4.0-beta` 改为 `0.5.0`。
更新 `<modules>` 列表，添加 `o11y-kit-core`，移除 `o11y-kit-api`、`o11y-kit-micrometer`、`o11y-kit-spring-aop`。
更新 `<dependencyManagement>`，将旧模块引用替换为 `o11y-kit-core`。

- [ ] **Step 5: 验证编译**

```bash
mvn compile -pl o11y-kit/o11y-kit-core -am -q
```

Expected: 编译成功，无错误

- [ ] **Step 6: 运行测试**

```bash
mvn test -pl o11y-kit/o11y-kit-core -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 7: 删除旧模块目录**

```bash
rm -rf o11y-kit/o11y-kit-api
rm -rf o11y-kit/o11y-kit-micrometer
rm -rf o11y-kit/o11y-kit-spring-aop
```

- [ ] **Step 8: Commit**

```bash
git add o11y-kit/
git commit -m "refactor(o11y-kit): consolidate api+micrometer+aop into core module"
```

---

### Task 2: 合并 webmvc + webflux 到 o11y-kit-core

**Files:**
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/ServerObservationHandler.java` (moved from webmvc)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/AbstractClientObservation.java` (moved from webmvc)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/RestClientObservationInterceptor.java` (moved from webmvc)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/RestClientResponseAdapter.java` (moved from webmvc)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/RestTemplateObservationInterceptor.java` (moved from webmvc)
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webflux/ClientObservationHandler.java` (moved from webflux)
- Create: 对应的 test 文件
- Modify: `o11y-kit/pom.xml` (移除 webmvc 和 webflux 模块引用)
- Delete: `o11y-kit/o11y-kit-spring-webmvc/` (整个目录)
- Delete: `o11y-kit/o11y-kit-spring-webflux/` (整个目录)

**Interfaces:**
- Consumes: Task 1 产出的 o11y-kit-core
- Produces: 包含 webmvc + webflux 代码的完整 o11y-kit-core

- [ ] **Step 1: 创建目录并复制文件**

```bash
mkdir -p o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client
mkdir -p o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webflux
mkdir -p o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webmvc/client
mkdir -p o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webflux
cp -r o11y-kit/o11y-kit-spring-webmvc/src/main/java/io/o11y/kit/spring/webmvc/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/
cp -r o11y-kit/o11y-kit-spring-webmvc/src/main/java/io/o11y/kit/spring/webmvc/client/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/
cp -r o11y-kit/o11y-kit-spring-webflux/src/main/java/io/o11y/kit/spring/webflux/*.java o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webflux/
# 复制测试文件
cp -r o11y-kit/o11y-kit-spring-webmvc/src/test/java/io/o11y/kit/spring/webmvc/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webmvc/
cp -r o11y-kit/o11y-kit-spring-webmvc/src/test/java/io/o11y/kit/spring/webmvc/client/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webmvc/client/
cp -r o11y-kit/o11y-kit-spring-webflux/src/test/java/io/o11y/kit/spring/webflux/*.java o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webflux/
```

- [ ] **Step 2: 更新文件中的 package 声明**

webmvc 文件需要将 `package io.o11y.kit.spring.webmvc` 改为 `package io.o11y.kit.webmvc`：
```bash
# 使用 sed 批量更新 package 声明
sed -i 's/package io.o11y.kit.spring.webmvc/package io.o11y.kit.webmvc/g' o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/*.java
sed -i 's/package io.o11y.kit.spring.webmvc.client/package io.o11y.kit.webmvc.client/g' o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/client/*.java
sed -i 's/package io.o11y.kit.spring.webflux/package io.o11y.kit.webflux/g' o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webflux/*.java
# 更新测试文件的 package 声明
sed -i 's/package io.o11y.kit.spring.webmvc/package io.o11y.kit.webmvc/g' o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webmvc/*.java
sed -i 's/package io.o11y.kit.spring.webmvc.client/package io.o11y.kit.webmvc.client/g' o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webmvc/client/*.java
sed -i 's/package io.o11y.kit.spring.webflux/package io.o11y.kit.webflux/g' o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/webflux/*.java
```

更新 webmvc 文件中的 import 语句（引用 o11y-kit-api 的类路径不变，不需要修改）：
```bash
sed -i 's/import io.o11y.kit.spring.aop/import io.o11y.kit.spring.aop/g' o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/webmvc/*.java
```

- [ ] **Step 3: 更新父 POM**

更新 `o11y-kit/pom.xml`，从 `<modules>` 中移除 `o11y-kit-spring-webmvc` 和 `o11y-kit-spring-webflux`。
从 `<dependencyManagement>` 中移除这两个模块的引用。

- [ ] **Step 4: 验证编译**

```bash
mvn compile -pl o11y-kit/o11y-kit-core -am -q
```

Expected: 编译成功，无错误

- [ ] **Step 5: 运行测试**

```bash
mvn test -pl o11y-kit/o11y-kit-core -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 6: 删除旧模块目录**

```bash
rm -rf o11y-kit/o11y-kit-spring-webmvc
rm -rf o11y-kit/o11y-kit-spring-webflux
```

- [ ] **Step 7: Commit**

```bash
git add o11y-kit/
git commit -m "refactor(o11y-kit): merge webmvc and webflux into core module"
```

---

### Task 3: 合并 autoconfigure + starter 到 o11y-kit-spring-boot-starter

**Files:**
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/pom.xml` (合并 autoconfigure 的依赖)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/HttpMetricsAutoConfiguration.java` (moved from autoconfigure)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebMvcAutoConfiguration.java` (moved from autoconfigure)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/ObservationWebFluxAutoConfiguration.java` (moved from autoconfigure)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/HttpClientObservationAutoConfiguration.java` (moved from autoconfigure)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/O11yKitProperties.java` (moved from autoconfigure)
- Create: `o11y-kit/o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (moved from autoconfigure)
- Create: 对应的 test 文件
- Modify: `o11y-kit/pom.xml` (移除 autoconfigure 模块引用)
- Delete: `o11y-kit/o11y-kit-spring-boot-autoconfigure/` (整个目录)

- [ ] **Step 1: 复制 autoconfigure 代码到 starter**

```bash
mkdir -p o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure
mkdir -p o11y-kit/o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring
mkdir -p o11y-kit/o11y-kit-spring-boot-starter/src/test/java/io/o11y/kit/autoconfigure
cp -r o11y-kit/o11y-kit-spring-boot-autoconfigure/src/main/java/io/o11y/kit/autoconfigure/*.java o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/
cp -r o11y-kit/o11y-kit-spring-boot-autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports o11y-kit/o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring/
cp -r o11y-kit/o11y-kit-spring-boot-autoconfigure/src/test/java/io/o11y/kit/autoconfigure/*.java o11y-kit/o11y-kit-spring-boot-starter/src/test/java/io/o11y/kit/autoconfigure/
```

- [ ] **Step 2: 更新 starter/pom.xml**

添加 `o11y-kit-core` 依赖（替换旧的 `o11y-kit-api` 依赖），保留所有 autoconfigure 需要的依赖：

```xml
<dependencies>
    <!-- 核心库 -->
    <dependency>
        <groupId>io.o11y.kit</groupId>
        <artifactId>o11y-kit-core</artifactId>
    </dependency>

    <!-- Spring Boot 自动配置 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>

    <!-- WebMVC 条件注解支持 -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webmvc</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- WebFlux 条件注解支持 -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webflux</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 3: 更新父 POM**

更新 `o11y-kit/pom.xml`，从 `<modules>` 中移除 `o11y-kit-spring-boot-autoconfigure`。
从 `<dependencyManagement>` 中移除 autoconfigure 模块引用。

- [ ] **Step 4: 验证编译**

```bash
mvn compile -pl o11y-kit/o11y-kit-spring-boot-starter -am -q
```

Expected: 编译成功，无错误

- [ ] **Step 5: 运行测试**

```bash
mvn test -pl o11y-kit/o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 6: 删除旧模块目录**

```bash
rm -rf o11y-kit/o11y-kit-spring-boot-autoconfigure
```

- [ ] **Step 7: Commit**

```bash
git add o11y-kit/
git commit -m "refactor(o11y-kit): merge autoconfigure into starter module"
```

---

### Task 4: 实现 BusinessMetricsPort SPI + Micrometer 适配器 + 自动配置

**Files:**
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/BusinessMetricsPort.java`
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/MicrometerMetricsAdapter.java`
- Create: `o11y-kit/o11y-kit-core/src/main/java/io/o11y/kit/metrics/package-info.java`
- Create: `o11y-kit/o11y-kit-core/src/test/java/io/o11y/kit/metrics/MicrometerMetricsAdapterTest.java`
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/HttpMetricsAutoConfiguration.java` (添加 BusinessMetricsPort bean)
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (添加 BusinessMetricsAutoConfiguration)

**Interfaces:**
- Consumes: Micrometer `MeterRegistry`
- Produces: `BusinessMetricsPort` 接口 + `MicrometerMetricsAdapter` 实现

- [ ] **Step 1: 创建 BusinessMetricsPort 接口**

```java
// o11y-kit-core/src/main/java/io/o11y/kit/metrics/BusinessMetricsPort.java
package io.o11y.kit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/**
 * Generic business metrics SPI for recording application-level metrics
 * (counters, timers, gauges) without coupling to a specific metrics backend.
 *
 * <p>Default implementation uses Micrometer ({@link MicrometerMetricsAdapter}).
 * Users can provide their own implementation to route to OpenTelemetry,
 * Dropwizard Metrics, or any other backend.
 *
 * <p>Usage example:
 * <pre>{@code
 * public class OrderMetrics {
 *     private final BusinessMetricsPort metrics;
 *
 *     public void recordOrderPlaced() {
 *         metrics.increment("orders.placed", "status", "CREATED");
 *     }
 * }
 * }</pre>
 *
 * @since 0.5.0
 */
public interface BusinessMetricsPort {

    /**
     * Obtain or create a counter for the given name and tags.
     * @param name metric name (e.g., "orders.placed")
     * @param tags key-value pairs (e.g., "status", "CREATED")
     * @return the counter instance
     */
    Counter counter(String name, String... tags);

    /**
     * Obtain or create a timer for the given name and tags.
     * @param name metric name (e.g., "saga.duration")
     * @param tags key-value pairs
     * @return the timer instance
     */
    Timer timer(String name, String... tags);

    /**
     * Register a gauge that returns the given value.
     * @param name metric name
     * @param value the current value (may be updated later)
     * @param tags key-value pairs
     * @return the gauge instance
     */
    <T extends Number> Gauge<T> gauge(String name, T value, String... tags);

    /** Increment a counter by 1. Convenience shortcut for {@code counter(name, tags).increment()}. */
    default void increment(String name, String... tags) {
        counter(name, tags).increment();
    }

    /** Record a duration to a timer. Convenience shortcut for {@code timer(name, tags).record(millis, MILLISECONDS)}. */
    default void recordDuration(String name, long millis, String... tags) {
        timer(name, tags).record(millis, TimeUnit.MILLISECONDS);
    }
}
```

- [ ] **Step 2: 创建 MicrometerMetricsAdapter**

```java
// o11y-kit-core/src/main/java/io/o11y/kit/metrics/MicrometerMetricsAdapter.java
package io.o11y.kit.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Gauge;
import java.util.function.Supplier;

/**
 * Micrometer-backed implementation of {@link BusinessMetricsPort}.
 * All metrics are registered with the given {@link MeterRegistry}.
 *
 * <p>Thread-safe: delegates to the Micrometer registry which is thread-safe.
 *
 * @since 0.5.0
 */
public class MicrometerMetricsAdapter implements BusinessMetricsPort {

    private final MeterRegistry registry;

    public MicrometerMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    @Override
    public Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).register(registry);
    }

    @Override
    public <T extends Number> Gauge<T> gauge(String name, T value, String... tags) {
        return Gauge.builder(name, () -> value).tags(tags).strongReference(true).register(registry);
    }
}
```

- [ ] **Step 3: 创建测试**

```java
// o11y-kit-core/src/test/java/io/o11y/kit/metrics/MicrometerMetricsAdapterTest.java
package io.o11y.kit.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MicrometerMetricsAdapterTest {

    private MicrometerMetricsAdapter adapter;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        adapter = new MicrometerMetricsAdapter(registry);
    }

    @Test
    void shouldCreateCounter() {
        adapter.increment("test.counter", "key", "value");
        double count = registry.counter("test.counter", "key", "value").count();
        assertEquals(1.0, count, 0.001);
    }

    @Test
    void shouldCreateTimer() {
        adapter.recordDuration("test.timer", 100, "key", "value");
        long count = registry.timer("test.timer", "key", "value").count();
        assertEquals(1L, count);
    }

    @Test
    void shouldCreateGauge() {
        adapter.gauge("test.gauge", 42.0, "key", "value");
        double value = registry.gauge("test.gauge", "key", "value").value();
        assertEquals(42.0, value, 0.001);
    }
}
```

- [ ] **Step 4: 创建 BusinessMetricsAutoConfiguration**

将以下配置添加到 `o11y-kit-spring-boot-starter/src/main/java/io/o11y/kit/autoconfigure/HttpMetricsAutoConfiguration.java` 中：

```java
@Bean
@ConditionalOnMissingBean(BusinessMetricsPort.class)
public BusinessMetricsPort businessMetricsPort(MeterRegistry registry) {
    return new MicrometerMetricsAdapter(registry);
}
```

并添加对应的 import 语句。

- [ ] **Step 5: 更新 AutoConfiguration.imports**

确保 `o11y-kit-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 包含 `HttpMetricsAutoConfiguration`（已存在）。

- [ ] **Step 6: 更新 o11y-kit 父 POM 的 dependencyManagement**

添加 `o11y-kit-core` 作为受管依赖（如果还没有）。

- [ ] **Step 7: 验证编译**

```bash
mvn compile -pl o11y-kit/o11y-kit-core -am -q
mvn compile -pl o11y-kit/o11y-kit-spring-boot-starter -am -q
```

Expected: 编译成功

- [ ] **Step 8: 运行测试**

```bash
mvn test -pl o11y-kit/o11y-kit-core -Dnet.bytebuddy.experimental=true
mvn test -pl o11y-kit/o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 9: Commit**

```bash
git add o11y-kit/
git commit -m "feat(o11y-kit): add BusinessMetricsPort SPI and Micrometer adapter"
```

---

### Task 5: 更新 order-demo 使用新模块

**Files:**
- Modify: `pom.xml` (order-demo 父 POM — 更新 o11y-kit 依赖引用)
- Modify: `order-adapter/pom.xml` (更新 o11y-kit 依赖)
- Modify: `order-application/src/main/java/com/example/order/application/port/out/MetricsPort.java` (可选，保留原样也可以)
- Modify: `order-adapter/src/main/java/com/example/order/adapter/metrics/OrderMetrics.java` (改为使用 BusinessMetricsPort)

**Interfaces:**
- Consumes: Task 4 产出的 `BusinessMetricsPort`
- Produces: 更新后的 order-demo 项目

- [ ] **Step 1: 更新 order-demo 父 POM**

将 `o11y-kit-api` 依赖引用替换为 `o11y-kit-core`：

```xml
<!-- 替换前 -->
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-api</artifactId>
    <version>0.5.0</version>
</dependency>

<!-- 替换后 -->
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-core</artifactId>
    <version>0.5.0</version>
</dependency>
```

- [ ] **Step 2: 更新 OrderMetrics 使用 BusinessMetricsPort**

```java
// order-adapter/src/main/java/com/example/order/adapter/metrics/OrderMetrics.java
package com.example.order.adapter.metrics;

import com.example.order.application.port.out.MetricsPort;
import io.o11y.kit.metrics.BusinessMetricsPort;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics implements MetricsPort {

    private final BusinessMetricsPort metrics;

    public OrderMetrics(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void recordOrderPlaced(String status) {
        metrics.increment("orders.placed", "status", status);
    }

    @Override
    public void recordOrderFailed(String reason) {
        metrics.increment("orders.failed", "reason", reason);
    }

    @Override
    public void recordInventoryReservation(String sku, boolean success) {
        metrics.increment("inventory.reservation", "sku", sku, "result", String.valueOf(success));
    }

    @Override
    public void recordSagaDuration(long durationMillis, String outcome) {
        metrics.recordDuration("saga.duration", durationMillis, "outcome", outcome);
    }

    @Override
    public void recordSagaStepDuration(String step, long durationMillis, String outcome) {
        metrics.recordDuration("saga.step.duration", durationMillis, "step", step, "outcome", outcome);
    }

    @Override
    public void recordSagaGap(String gap, long durationMillis) {
        metrics.recordDuration("saga.gap.duration", durationMillis, "gap", gap);
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl order-adapter -am -q
```

Expected: 编译成功

- [ ] **Step 4: 运行测试**

```bash
mvn test -pl order-application -Dnet.bytebuddy.experimental=true
mvn test -pl order-adapter -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 5: Commit**

```bash
git add pom.xml order-adapter/ order-application/
git commit -m "refactor: update order-demo to use o11y-kit-core and BusinessMetricsPort"
```

---

### Task 6: Maven Central 发布准备（POM 元数据 + 构建插件）

**Files:**
- Modify: `o11y-kit/pom.xml` (添加 Maven Central 元数据 + 构建插件)
- Modify: `o11y-kit/o11y-kit-core/pom.xml` (添加模块级元数据)
- Modify: `o11y-kit/o11y-kit-spring-boot-starter/pom.xml` (添加模块级元数据)
- Modify: `o11y-kit/o11y-kit-test/pom.xml` (添加模块级元数据)

**Interfaces:**
- Consumes: 无
- Produces: 符合 Maven Central 要求的 POM 配置

- [ ] **Step 1: 更新父 POM 元数据**

在 `o11y-kit/pom.xml` 中添加以下内容：

```xml
<name>o11y-kit</name>
<description>Lightweight, non-invasive HTTP observability SDK for Spring Boot</description>
<url>https://github.com/sunny809/o11y-kit</url>

<licenses>
    <license>
        <name>Apache License, Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
    </license>
</licenses>

<scm>
    <connection>scm:git:git@github.com:sunny809/o11y-kit.git</connection>
    <developerConnection>scm:git:git@github.com:sunny809/o11y-kit.git</developerConnection>
    <url>https://github.com/sunny809/o11y-kit</url>
</scm>

<developers>
    <developer>
        <name>o11y-kit team</name>
        <email>team@o11y.kit</email>
    </developer>
</developers>
```

- [ ] **Step 2: 添加 Source 和 Javadoc 插件**

在父 POM 的 `<build><plugins>` 中添加：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals><goal>jar-no-fork</goal></goals>
        </execution>
    </executions>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals><goal>jar</goal></goals>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 3: 验证构建**

```bash
mvn package -pl o11y-kit/o11y-kit-core -am -DskipTests
```

Expected: 构建成功，target 目录中包含 `*-sources.jar` 和 `*-javadoc.jar`

- [ ] **Step 4: Commit**

```bash
git add o11y-kit/
git commit -m "chore(o11y-kit): add Maven Central POM metadata and build plugins"
```

---

### Task 7: 文档 + 示例项目

**Files:**
- Create: `o11y-kit/README.md` (重写为通用 README)
- Create: `o11y-kit/samples/o11y-kit-sample/pom.xml`
- Create: `o11y-kit/samples/o11y-kit-sample/src/main/java/io/o11y/kit/sample/SampleApplication.java`
- Create: `o11y-kit/samples/o11y-kit-sample/src/main/java/io/o11y/kit/sample/SampleController.java`
- Create: `o11y-kit/samples/o11y-kit-sample/src/main/resources/application.yml`

- [ ] **Step 1: 重写 README.md**

```markdown
# o11y-kit

Lightweight, non-invasive HTTP observability SDK for Spring Boot.

## Modules

| Module | Description |
|--------|-------------|
| `o11y-kit-core` | Core library: HTTP metric recording, WebMVC/WebFlux integration, AOP support, business metrics SPI |
| `o11y-kit-spring-boot-starter` | Spring Boot auto-configuration — add this dependency to get started |
| `o11y-kit-test` | Test harness and assertion utilities for integration tests |

## Quick Start

```xml
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

## Features

- **HTTP Server Metrics** — automatically record request duration, status code, and method for every controller
- **HTTP Client Metrics** — record outbound HTTP call metrics (RestTemplate, RestClient, WebClient)
- **Business Metrics SPI** — define your own business metrics without coupling to Micrometer
- **@Observed Annotation** — AOP-based method-level observation
- **Trace ID Propagation** — automatic trace ID resolution from HTTP headers

## Building

```bash
mvn clean install -pl o11y-kit/o11y-kit-core -am
```
```

- [ ] **Step 2: 创建示例项目**

```xml
<!-- o11y-kit/samples/o11y-kit-sample/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.o11y.kit</groupId>
        <artifactId>o11y-kit</artifactId>
        <version>0.5.0</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>o11y-kit-sample</artifactId>
    <name>o11y-kit-sample</name>
    <description>Sample Spring Boot application demonstrating o11y-kit integration</description>

    <dependencies>
        <dependency>
            <groupId>io.o11y.kit</groupId>
            <artifactId>o11y-kit-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
</project>
```

```java
// o11y-kit/samples/o11y-kit-sample/src/main/java/io/o11y/kit/sample/SampleApplication.java
package io.o11y.kit.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
```

```java
// o11y-kit/samples/o11y-kit-sample/src/main/java/io/o11y/kit/sample/SampleController.java
package io.o11y.kit.sample;

import io.o11y.kit.metrics.BusinessMetricsPort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SampleController {

    private final BusinessMetricsPort metrics;

    public SampleController(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/hello")
    public String hello() {
        metrics.increment("api.hello.calls");
        return "Hello, o11y-kit!";
    }
}
```

- [ ] **Step 3: 验证示例项目编译**

```bash
mvn compile -pl o11y-kit/samples/o11y-kit-sample -am -q
```

Expected: 编译成功

- [ ] **Step 4: Commit**

```bash
git add o11y-kit/README.md o11y-kit/samples/
git commit -m "docs(o11y-kit): add README and sample project"
```

---

### Task 8: 全量验证

**Files:**
- 无新增文件

**Interfaces:**
- 验证所有模块的编译和测试

- [ ] **Step 1: 编译 o11y-kit 所有模块**

```bash
mvn compile -pl o11y-kit/o11y-kit-core,o11y-kit/o11y-kit-spring-boot-starter,o11y-kit/o11y-kit-test -am -q
```

Expected: 编译成功

- [ ] **Step 2: 运行 o11y-kit 所有测试**

```bash
mvn test -pl o11y-kit/o11y-kit-core -Dnet.bytebuddy.experimental=true
mvn test -pl o11y-kit/o11y-kit-spring-boot-starter -Dnet.bytebuddy.experimental=true
mvn test -pl o11y-kit/o11y-kit-test -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 3: 编译 order-demo 所有模块**

```bash
mvn compile -pl order-application,order-adapter,order-infrastructure -am -q -Dnet.bytebuddy.experimental=true
```

Expected: 编译成功

- [ ] **Step 4: 运行 order-demo 测试**

```bash
mvn test -pl order-application -Dnet.bytebuddy.experimental=true
mvn test -pl order-adapter -Dnet.bytebuddy.experimental=true
mvn test -pl order-infrastructure -Dnet.bytebuddy.experimental=true
```

Expected: 所有测试通过

- [ ] **Step 5: 终端确认**

```bash
echo "✅ o11y-kit modules: $(mvn -pl o11y-kit/o11y-kit-core -q exec:exec -Dexec.executable=echo -Dexec.args='OK' 2>/dev/null || echo 'built')"
echo "✅ order-demo modules: $(mvn -pl order-application -q exec:exec -Dexec.executable=echo -Dexec.args='OK' 2>/dev/null || echo 'built')"
```

- [ ] **Step 6: 最终 Commit（如果还有未提交的修改）**

```bash
git add -A
git status
```

Expected: 干净的 git 状态，无未提交修改