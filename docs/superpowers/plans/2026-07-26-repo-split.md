# Repo 拆分 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 monorepo 拆分为两个独立仓库：order-demo（六边形架构 + Saga 模式）和 o11y-kit（观测 SDK）。

**Architecture:** 两个 repo 通过 Maven 依赖关联，互不包含对方代码。order-demo 清理 order-o11y 模块、统一包名；o11y-kit 独立仓库附带文档站点和 CI/CD 流水线。

**Tech Stack:** Java 25, Spring Boot 3.4.3, Maven, mkdocs, GitHub Actions

## Global Constraints

- 包名替换必须一致：`com.order.demo` → `com.order.demo`
- 所有现有测试必须在包名替换后继续通过
- order-demo 不再包含 o11y-kit 目录
- 两个 repo 的 CI 必须独立可运行
- 先完成 order-demo 的改造，再创建 o11y-kit 独立 repo

---

### Task 1: 包名替换 — com.order.demo → com.order.demo

**Files:**
- Modify: 所有 Java 文件、pom.xml、application.yml、properties 文件

**Interfaces:**
- Consumes: 无
- Produces: 统一后的包名 `com.order.demo`

- [ ] **Step 1: 确认当前包名分布**

```bash
cd /home/bjdeng/project/java_projects/order-demo
grep -r "com\.example\.order" --include="*.java" --include="*.xml" --include="*.yml" --include="*.properties" -l | grep -v target/ | grep -v o11y-kit/ | sort
```

记录所有需要修改的文件列表。

- [ ] **Step 2: 执行批量替换**

```bash
cd /home/bjdeng/project/java_projects/order-demo
# 排除 o11y-kit 目录（它使用 io.o11y.kit 包名，不受影响）
find . -path ./o11y-kit -prune -o \( -name "*.java" -o -name "*.xml" -o -name "*.yml" -o -name "*.properties" \) -print \
  | grep -v target/ \
  | xargs sed -i 's/com\.example\.order/com.order.demo/g'
```

- [ ] **Step 3: 验证替换结果**

```bash
grep -r "com\.example\.order" --include="*.java" --include="*.xml" --include="*.yml" --include="*.properties" -l | grep -v target/ | grep -v o11y-kit/ | wc -l
```

Expected: 0（没有剩余旧的包名）

- [ ] **Step 4: 验证编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo
mvn compile -pl order-application,order-adapter,order-infrastructure -am -Dnet.bytebuddy.experimental=true -Djacoco.skip=true -q
```

Expected: 编译成功

- [ ] **Step 5: 运行测试**

```bash
cd /home/bjdeng/project/java_projects/order-demo
mvn test -pl order-application -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: 82 tests, 0 failures

- [ ] **Step 6: Commit**

```bash
cd /home/bjdeng/project/java_projects/order-demo
git add -A
git commit -m "refactor: rename package com.order.demo to com.order.demo"
```

---

### Task 2: 删除 order-o11y 模块，合并 TracerHelper

**Files:**
- Create: `order-adapter/src/main/java/com/order/demo/adapter/observability/TracerHelper.java`
- Delete: `order-o11y/` (整个目录)
- Modify: `pom.xml` (从 modules 中移除 order-o11y)
- Modify: `order-adapter/pom.xml` (移除 order-o11y 依赖)

**Interfaces:**
- Consumes: 无
- Produces: 清理后的 order-demo 模块结构

- [ ] **Step 1: 复制 TracerHelper 到 order-adapter**

```bash
mkdir -p order-adapter/src/main/java/com/order/demo/adapter/observability
cp order-o11y/src/main/java/com/example/order/o11y/util/TracerHelper.java order-adapter/src/main/java/com/order/demo/adapter/observability/
```

注意：包名已在 Task 1 中替换，所以原文件路径中的 `com/example/order` 已经变为 `com/order/demo`。

- [ ] **Step 2: 更新 TracerHelper 的 package 声明**

```bash
# 替换 package 声明
sed -i 's/package com.order.demo.o11y.util/package com.order.demo.adapter.observability/' order-adapter/src/main/java/com/order/demo/adapter/observability/TracerHelper.java
```

- [ ] **Step 3: 更新父 POM**

从 `pom.xml` 的 `<modules>` 中移除 `<module>order-o11y</module>`。

- [ ] **Step 4: 更新 order-adapter/pom.xml**

移除 `order-o11y` 依赖项。

- [ ] **Step 5: 删除 order-o11y 目录**

```bash
rm -rf order-o11y
```

- [ ] **Step 6: 验证编译**

```bash
mvn compile -pl order-application,order-adapter,order-infrastructure -am -Dnet.bytebuddy.experimental=true -Djacoco.skip=true -q
```

- [ ] **Step 7: 运行测试**

```bash
mvn test -pl order-application,order-adapter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: remove order-o11y module, merge TracerHelper into order-adapter"
```

---

### Task 3: 删除 o11y-kit 目录，更新依赖为 Maven 引入

**Files:**
- Delete: `o11y-kit/` (整个目录)
- Modify: `pom.xml` (更新 o11y-kit 依赖版本管理)
- Modify: `order-adapter/pom.xml` (更新 o11y-kit 依赖)

**Interfaces:**
- Consumes: 无
- Produces: 从 Maven Central 引入 o11y-kit 的 order-demo

- [ ] **Step 1: 删除 o11y-kit 目录**

```bash
rm -rf o11y-kit
```

- [ ] **Step 2: 更新 pom.xml 依赖管理**

在 `pom.xml` 的 `<dependencyManagement>` 中，确认 o11y-kit 依赖使用 Maven Central 版本（目前是 `0.5.0`）。

```xml
<dependency>
    <groupId>io.o11y.kit</groupId>
    <artifactId>o11y-kit-spring-boot-starter</artifactId>
    <version>0.5.0</version>
</dependency>
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl order-application,order-adapter,order-infrastructure -am -Dnet.bytebuddy.experimental=true -Djacoco.skip=true -q
```

如果 o11y-kit 尚未发布到 Maven Central，需要先在本地安装：
```bash
# 从 o11y-kit 独立 repo 构建并安装到本地仓库
cd /path/to/o11y-kit
mvn install -DskipTests -Djacoco.skip=true
```

- [ ] **Step 4: 运行测试**

```bash
mvn test -pl order-application,order-adapter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove o11y-kit directory, switch to Maven dependency"
```

---

### Task 4: 更新 order-demo CI/CD

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/codeql.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: 无
- Produces: 不需要本地构建 o11y-kit 的 CI 流水线

- [ ] **Step 1: 更新 ci.yml**

删除 `Build o11y-kit (dependency)` 步骤，以及 `Static analysis for o11y-kit` 步骤。

```yaml
# 删除以下内容
- name: Build o11y-kit (dependency)
  run: mvn clean install -DskipTests -f o11y-kit/pom.xml -B
```

- [ ] **Step 2: 更新 codeql.yml**

删除 `Build o11y-kit (dependency)` 步骤。

- [ ] **Step 3: 更新 release.yml**

删除 o11y-kit 相关步骤，只保留 order-demo 的构建和发布。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: update CI/CD to remove o11y-kit local build steps"
```

---

### Task 5: 创建 o11y-kit 独立 repo

**Files:**
- Create: 新 GitHub 仓库 `sunny809/o11y-kit`
- Create: 复制所有 o11y-kit 代码到新仓库

**Interfaces:**
- Consumes: 无
- Produces: 独立的 o11y-kit 仓库

- [ ] **Step 1: 在 GitHub 上创建空仓库**

```bash
gh repo create sunny809/o11y-kit --private --description "Lightweight, non-invasive HTTP observability SDK for Spring Boot"
```

- [ ] **Step 2: 复制 o11y-kit 代码到新仓库**

```bash
# 从 order-demo 的 git 历史中提取 o11y-kit 代码
cd /tmp
git clone /home/bjdeng/project/java_projects/order-demo o11y-kit-temp
cd o11y-kit-temp
# 使用 git filter-branch 或 git subtree 提取 o11y-kit 历史
# 或者直接复制最新代码（更简单）
mkdir ~/o11y-kit
cp -r o11y-kit/* ~/o11y-kit/
rm -rf o11y-kit-temp
```

- [ ] **Step 3: 初始化新仓库**

```bash
cd ~/o11y-kit
git init
git add -A
git commit -m "feat: initial o11y-kit SDK commit"
git remote add origin git@github.com:sunny809/o11y-kit.git
git push -u origin main
```

- [ ] **Step 4: 验证新仓库构建**

```bash
cd ~/o11y-kit
mvn compile -pl o11y-kit-core -am -Djacoco.skip=true -q
```

---

### Task 6: 添加 o11y-kit 文档站点

**Files:**
- Create: `docs/mkdocs.yml`
- Create: `docs/index.md`
- Create: `docs/getting-started.md`
- Create: `docs/modules.md`
- Create: `docs/http-observability.md`
- Create: `docs/configuration.md`
- Create: `docs/api/http.md`
- Create: `docs/api/observed.md`
- Create: `docs/api/properties.md`
- Create: `docs/contributing.md`
- Create: `.github/workflows/docs.yml`

**Interfaces:**
- Consumes: 无
- Produces: mkdocs 文档站点，部署到 GitHub Pages

- [ ] **Step 1: 创建 mkdocs.yml**

```yaml
site_name: o11y-kit
site_description: Lightweight HTTP observability SDK for Spring Boot
repo_url: https://github.com/sunny809/o11y-kit
theme: material
nav:
  - Home: index.md
  - Getting Started: getting-started.md
  - Modules: modules.md
  - HTTP Observability: http-observability.md
  - Configuration: configuration.md
  - API Reference:
    - HttpMetricRecorder: api/http.md
    - @Observed: api/observed.md
    - Properties: api/properties.md
  - Contributing: contributing.md
```

- [ ] **Step 2: 创建 docs/index.md**

```markdown
# o11y-kit

Lightweight, non-invasive HTTP observability SDK for Spring Boot.

- **HTTP Server Metrics** — automatic request duration, status code tracking
- **HTTP Client Metrics** — RestTemplate, RestClient, WebClient support
- **@Observed Annotation** — AOP-based method-level observation
- **Trace ID Propagation** — W3C traceparent support
```

- [ ] **Step 3: 创建 docs/getting-started.md**

包含 Maven 依赖坐标、最小配置、快速启动步骤。

- [ ] **Step 4: 创建 docs/modules.md**

说明三个模块的职责和依赖关系。

- [ ] **Step 5: 创建 docs/http-observability.md**

说明 HTTP 服务器端和客户端观测的工作原理。

- [ ] **Step 6: 创建 docs/configuration.md**

说明 `application.yml` 中的配置项。

- [ ] **Step 7: 创建 API 文档**

分别描述 `HttpMetricRecorder`、`@Observed` 注解、配置属性。

- [ ] **Step 8: 创建 docs.yml**

```yaml
name: Deploy Docs
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

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "docs: add mkdocs documentation site"
```

---

### Task 7: 添加 o11y-kit CI/CD + Release 流水线

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: 无
- Produces: 完整的 CI/CD 流水线

- [ ] **Step 1: 创建 ci.yml**

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - run: mvn test -pl o11y-kit-core -Djacoco.skip=true
```

- [ ] **Step 2: 创建 release.yml**

```yaml
name: Release
on:
  push:
    tags: ['v*']
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          server-id: ossrh
          server-username: OSSRH_USERNAME
          server-password: OSSRH_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
      - run: mvn deploy -DskipTests -Djacoco.skip=true
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: add CI/CD and release workflows"
```

---

### Task 8: 全量验证

**Files:**
- 无新增文件

- [ ] **Step 1: 验证 order-demo 编译**

```bash
cd /home/bjdeng/project/java_projects/order-demo
mvn compile -pl order-application,order-adapter,order-infrastructure -am -Dnet.bytebuddy.experimental=true -Djacoco.skip=true -q
```

Expected: 编译成功

- [ ] **Step 2: 验证 order-demo 测试**

```bash
mvn test -pl order-application -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
mvn test -pl order-adapter -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: 所有测试通过

- [ ] **Step 3: 验证 o11y-kit 编译**

```bash
cd ~/o11y-kit
mvn compile -pl o11y-kit-core -am -Djacoco.skip=true -q
```

Expected: 编译成功

- [ ] **Step 4: 验证 o11y-kit 测试**

```bash
mvn test -pl o11y-kit-core -Dnet.bytebuddy.experimental=true -Djacoco.skip=true 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: 64 tests, 0 failures

- [ ] **Step 5: 验证 order-demo 没有 o11y-kit 目录**

```bash
ls /home/bjdeng/project/java_projects/order-demo/o11y-kit 2>&1 || echo "✅ o11y-kit directory removed"
```

Expected: "No such file or directory"

- [ ] **Step 6: 验证 order-demo 没有 order-o11y 目录**

```bash
ls /home/bjdeng/project/java_projects/order-demo/order-o11y 2>&1 || echo "✅ order-o11y directory removed"
```

Expected: "No such file or directory"

- [ ] **Step 7: 验证没有残留的旧包名**

```bash
grep -r "com\.example\.order" --include="*.java" --include="*.xml" --include="*.yml" -l | grep -v target/ | grep -v o11y-kit/ | wc -l
```

Expected: 0