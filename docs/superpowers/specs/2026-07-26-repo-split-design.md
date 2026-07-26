# 项目拆分设计文档

> 将当前 monorepo 拆分为两个独立仓库：order-demo（六边形架构 + Saga 模式）和 o11y-kit（观测 SDK）。

**目标：** 将 o11y-kit 分离为独立仓库，并附带文档站点和 CI/CD 流水线；order-demo 清理冗余模块、统一包名、继续作为 o11y-kit 的使用示范。

**架构：** 两个 repo 通过 Maven 依赖关联（order-demo → o11y-kit），互不包含对方代码。

---

## 1. Repo 拆分边界

### o11y-kit（github.com/sunny809/o11y-kit）

```
o11y-kit/
├── o11y-kit-core/                    # HTTP 观测 + 指标记录 + AOP
├── o11y-kit-spring-boot-starter/    # 自动配置
├── o11y-kit-test/                    # 测试工具
├── samples/                          # 示例项目
├── docs/                             # 文档站点 (mkdocs)
│   ├── mkdocs.yml
│   ├── index.md
│   ├── getting-started.md
│   ├── http-observability.md
│   ├── configuration.md
│   └── api/
├── CHANGELOG.md
├── CONTRIBUTING.md
└── pom.xml
```

### order-demo（github.com/sunny809/order-demo）

```
order-demo/
├── order-application/      # 领域层 (port/domain/usecase)
├── order-adapter/          # 适配层 (controller/persistence/metrics)
├── order-infrastructure/   # Spring Boot 启动 + 配置
├── bdd-specs/              # BDD 测试
├── docs/                   # 架构文档
└── pom.xml
```

### 依赖关系

- `order-demo` 通过 Maven 依赖 `io.o11y.kit:o11y-kit-spring-boot-starter`
- `o11y-kit` 不依赖 `order-demo`
- `order-demo` 不再包含 `o11y-kit/` 目录

---

## 2. order-demo 重构

### 2.1 删除 order-o11y 模块

将 `TracerHelper` 合并到 `order-adapter`：

```
order-o11y/src/main/java/.../o11y/util/TracerHelper.java
    → order-adapter/src/main/java/.../adapter/observability/TracerHelper.java
```

删除 `order-o11y/pom.xml`，从父 POM 的 modules 中移除。

### 2.2 包名变更

`com.example.order` → `com.order.demo`

涉及所有 Java 源文件、测试文件、pom.xml、application.yml、properties 文件中的包名和坐标。

### 2.3 删除 o11y-kit 目录

从 order-demo 仓库中删除 `o11y-kit/` 目录，改为从 Maven Central 依赖引入。

---

## 3. o11y-kit 文档站点

### 技术选型

- mkdocs + Material for MkDocs 主题
- 纯 Markdown，零构建成本
- 部署到 GitHub Pages

### 目录结构

```
docs/
├── mkdocs.yml
├── index.md
├── getting-started.md
├── modules.md
├── http-observability.md
├── configuration.md
├── api/
│   ├── http.md
│   ├── observed.md
│   └── properties.md
├── samples/
│   └── sample-app.md
└── contributing.md
```

### GitHub Actions 部署

```yaml
on:
  push:
    branches: [main]
    paths: ['docs/**']
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.x' }
      - run: pip install mkdocs mkdocs-material
      - run: mkdocs gh-deploy --force
```

---

## 4. CI/CD 流水线

### o11y-kit CI

Java 25 + Maven 构建，测试 o11y-kit-core 和 starter 模块。

### o11y-kit Release

打 tag `v*` 时自动发布到 Maven Central：
- 配置 GPG 签名
- 推送到 OSSRH staging
- 发布源码 JAR 和 Javadoc JAR

### order-demo CI

从 Maven Central 获取 o11y-kit 依赖，不再需要本地构建 o11y-kit。

---

## 5. 实施顺序

| 步骤 | 内容 | 涉及 |
|------|------|------|
| 1 | 包名替换：`com.example.order` → `com.order.demo` | order-demo 全项目 |
| 2 | 删除 order-o11y 模块，合并 TracerHelper | order-demo |
| 3 | 创建 o11y-kit 独立 repo，复制代码 | 新仓库 |
| 4 | 从 order-demo 删除 o11y-kit/ 目录 | order-demo |
| 5 | 更新 order-demo 依赖，改为从 Maven 引入 | pom.xml |
| 6 | 更新 order-demo CI/CD | .github/workflows |
| 7 | 添加 o11y-kit 文档站点 (mkdocs) | o11y-kit |
| 8 | 添加 o11y-kit CI/CD + Release 流水线 | o11y-kit |
| 9 | 全量验证 | 两个 repo |