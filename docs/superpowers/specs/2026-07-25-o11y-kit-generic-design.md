# o11y-kit 通用化设计文档

> 将 o11y-kit 从 order-demo 的附属模块改造为独立可复用的通用观测 SDK，支持 Maven Central 发布。

**目标：** 将 o11y-kit 改造为独立可复用的通用观测 SDK，支持其他 Spring 应用通过一行依赖快速接入完整的 HTTP 观测、业务指标 SPI 和分布式追踪能力。

**架构：** 当前 8 模块合并为 3 模块（core + starter + test），提供通用的 BusinessMetricsPort SPI 替代现有的 order-demo 特有 MetricsPort，所有 optional 依赖实现按需加载。

**技术栈：** Spring Boot 3.4.3, Micrometer 1.14.5, OpenTelemetry 1.37.0, Maven Central

---

## 1. 模块合并

### 当前结构（8 模块）

```
o11y-kit/
├── o11y-kit-core                         # 纯接口
├── o11y-kit-core                  # Micrometer 实现
├── o11y-kit-core              # WebMVC 观测
├── o11y-kit-core             # WebFlux 观测
├── o11y-kit-core                 # @Observed 切面
├── o11y-kit-spring-boot-starter  # 自动配置
├── o11y-kit-spring-boot-starter        # 启动器
└── o11y-kit-test                       # 测试工具
```

### 目标结构（3 模块）

```
o11y-kit/
├── o11y-kit-core/          # 核心库 — 合并 api + micrometer + aop + webmvc + webflux
│   ├── io.o11y.kit.http    # HttpMetricRecorder + 实现
│   ├── io.o11y.kit.metrics # BusinessMetricsPort SPI + Micrometer 实现
│   ├── io.o11y.kit.aop     # @Observed + 切面
│   ├── io.o11y.kit.webmvc  # Server/Client 观测拦截器
│   └── io.o11y.kit.webflux # WebFlux 观测 Handler
│
├── o11y-kit-spring-boot-starter/  # 启动器 — 合并 autoconfigure + starter
│   └── 自动配置类 + spring.factories / org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└── o11y-kit-test/                  # 测试工具保持不变
    └── OtelTestHarness + MetricsAssertions
```

### 依赖管理

- `o11y-kit-core` 依赖 Micrometer（compile）、OpenTelemetry API（optional）、Spring WebMVC（optional）、Spring WebFlux（optional）
- `o11y-kit-spring-boot-starter` 依赖 `o11y-kit-core`
- `o11y-kit-test` 依赖 `o11y-kit-core`（test scope） + OpenTelemetry SDK（test scope）

### 用户使用方式

```xml
<!-- 核心功能（不含自动配置） -->
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-core</artifactId>
    <version>0.5.0</version>
</dependency>

<!-- 一键启动（含自动配置） -->
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

---

## 2. BusinessMetricsPort SPI

### 通用 SPI 接口

```java
package io.o11y.kit.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.common.lang.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * Generic business metrics SPI. Implementations translate these calls
 * to Micrometer, OpenTelemetry, or any other metrics backend.
 *
 * <p>Note: returns Micrometer types ({@link Counter}, {@link Timer}, {@link Gauge})
 * for fluent API compatibility. Users who prefer a different backend can
 * implement this interface with their own adapter.
 *
 * @since 0.5.0
 */
public interface BusinessMetricsPort {

    /** 自增计数器 */
    Counter counter(String name, String... tags);

    /** 记录耗时分布 */
    Timer timer(String name, String... tags);

    /** 记录当前值 */
    <T extends Number> Gauge<T> gauge(String name, @Nullable T value, String... tags);

    // 便捷方法
    default void increment(String name, String... tags) {
        counter(name, tags).increment();
    }

    default void recordDuration(String name, long millis, String... tags) {
        timer(name, tags).record(millis, TimeUnit.MILLISECONDS);
    }
}
```

### Micrometer 适配器

```java
package io.o11y.kit.metrics;

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

### 自动配置

```java
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class BusinessMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BusinessMetricsPort.class)
    public BusinessMetricsPort businessMetricsPort(MeterRegistry registry) {
        return new MicrometerMetricsAdapter(registry);
    }
}
```

### 用户应用层使用示例

```java
// 用户定义自己的业务指标接口
public interface OrderMetricsPort {
    void recordOrderPlaced(String status);
    void recordSagaDuration(long ms, String outcome);
}

// 用户通过 SPI 实现
@Component
public class OrderMetricsAdapter implements OrderMetricsPort {
    private final BusinessMetricsPort metrics;

    public OrderMetricsAdapter(BusinessMetricsPort metrics) {
        this.metrics = metrics;
    }

    @Override
    public void recordOrderPlaced(String status) {
        metrics.increment("orders.placed", "status", status);
    }

    @Override
    public void recordSagaDuration(long ms, String outcome) {
        metrics.recordDuration("saga.duration", ms, "outcome", outcome);
    }
}
```

---

## 3. Maven Central 发布准备

### POM 元数据

所有模块 POM 需补充以下信息：

```xml
<name>o11y-kit-core</name>
<description>Lightweight, non-invasive HTTP observability SDK for Spring Boot — Core module</description>
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

### 构建插件配置

```xml
<!-- Source JAR -->
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

<!-- Javadoc JAR -->
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

<!-- GPG 签名（账号到位后启用） -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <executions>
        <execution>
            <id>sign-artifacts</id>
            <goals><goal>sign</goal></goals>
        </execution>
    </executions>
</plugin>
```

### 版本号策略

- 当前版本：`0.4.0-beta`
- 合并后首版：`0.5.0`（包含模块合并 + SPI 提取）
- 遵循 SemVer 2.0：`MAJOR.MINOR.PATCH`

---

## 4. 文档计划

### README 内容
- 项目简介（目标用户、解决的问题）
- 快速开始（Spring Boot 项目接入步骤）
- 模块说明（3 个模块的职责和依赖关系）
- 核心功能详解（HTTP 观测、业务指标 SPI、@Observed 切面）
- 配置项参考（application.yml 配置）
- 常见问题

### Javadoc
- 所有 public API 方法必须有 Javadoc（@param / @return / @since）
- 包级 Javadoc 说明每个包的职责

### 示例项目
- 在 `o11y-kit-samples/` 目录下创建一个最小 Spring Boot 示例项目
- 展示：HTTP 请求自动观测 + 业务指标 SPI 使用 + @Observed 注解

---

## 5. 实施顺序

| 步骤 | 内容 | 涉及模块 |
|------|------|---------|
| 1 | 合并 api + micrometer + aop 代码到 core | core |
| 2 | 合并 webmvc + webflux 到 core | core |
| 3 | 合并 autoconfigure + starter 到 starter | starter |
| 4 | 实现 BusinessMetricsPort SPI + Micrometer 适配器 | core |
| 5 | 补充 BusinessMetricsAutoConfiguration | starter |
| 6 | 更新 POM 元数据 + 构建插件配置 | 所有模块 |
| 7 | 更新 README + Javadoc | 所有模块 |
| 8 | 创建示例项目 | samples |
| 9 | 验证：编译、测试、示例项目正常运行 | 全部 |