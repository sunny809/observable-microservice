# Sprint 4 Backlog — o11y-kit API Maturity

**Version:** v0.4.0-beta

**Sprint Goal:**
Complete o11y-kit's core capability from "HTTP auto-instrumentation" to "developer-declared method observation",
and fill the Server configuration placeholder left empty since Sprint 2.

**Theme:** Complete o11y-kit SDK core capabilities

**Duration:** ~6-8 days (5-7 engineering + 1 TW)

**Total stories:** 3 (P0: 2, P1: 1)

---

## User Stories

### US-4.1: [P0] `@Observed` annotation — AOP method-level observation

- **Priority:** P0
- **Effort:** L (3-5 days)
- **Area:** o11y-kit (new module: `o11y-kit-spring-aop`)

**As a** Spring Boot developer
**I want** to annotate any service method with `@Observed`
**So that** I get automatic Micrometer timing + optional OTel span without manual Timer boilerplate

**Acceptance Criteria:**
- [ ] AC1: `@Observed` on a `@Service` method produces `o11y_observed_duration_seconds` in Prometheus
- [ ] AC2: Custom `name` attribute overrides default metric name
- [ ] AC3: Custom `tags` attribute appears as additional metric tags
- [ ] AC4: Exception-throwing methods record `outcome="error"`
- [ ] AC5: Auto-configuration works with `spring-boot-starter-aop` on classpath
- [ ] AC6: Existing HTTP auto-instrumentation is unaffected

**Tech notes:**
- New module: `o11y-kit-spring-aop`
- Files: `Observed.java` (annotation), `ObservedAspect.java` (AOP aspect), `ObservedAutoConfiguration.java`, `pom.xml`, `package-info.java`, tests
- Dependencies: `o11y-kit-api` + `micrometer-core` + `spring-aop` + `aspectjweaver`
- Module must be added to parent POM, starter POM, and AutoConfiguration.imports

---

### US-4.2: [P0] `o11y.kit.server.*` 配置属性

- **Priority:** P0
- **Effort:** S (0.5-1 day)
- **Area:** o11y-kit-spring-boot-autoconfigure

**As a** DevOps engineer
**I want** to configure server-side observation via `application.yml` (enable/disable, exclude patterns)
**So that** I can exclude actuator endpoints, health checks, or custom paths without code changes

**Acceptance Criteria:**
- [ ] AC1: Setting `o11y.kit.server.enabled=false` disables `ServerObservationHandler` registration
- [ ] AC2: Setting `o11y.kit.server.exclude-patterns[0]=/custom/**` excludes `/custom/` from metrics
- [ ] AC3: Default patterns (`/actuator/**`, `/health/**`) match existing behavior
- [ ] AC4: `o11y.kit.server.metrics.enabled=false` stops timer/counter emission but keeps trace ID header injection

**Tech notes:**
- Fill `O11yKitProperties.Server` inner class (currently empty placeholder since Sprint 2)
- Wire into `ObservationWebMvcAutoConfiguration` with `@ConditionalOnProperty`
- Test with `ApplicationContextRunner`

---

### US-4.3: [P1] User documentation

- **Priority:** P1
- **Effort:** S (1 day TW)
- **Area:** o11y-kit/README.md + CHANGELOG.md

**As a** Developer evaluating o11y-kit
**I want** to read about `@Observed` annotation usage, server configuration reference, and examples
**So that** I can adopt the new features without reading source code

**Deliverables:**
1. o11y-kit/README.md: module table update, dependency graph, configuration table, metrics table, new "Method-level observation" section
2. o11y-kit/CHANGELOG.md: v0.4.0-beta release notes
3. Sprint 4 docs in `docs/sprints/sprint-4/`

---

## Effort Summary

| Story | Priority | Area | Effort | Dependencies |
|-------|----------|------|--------|-------------|
| US-4.1 `@Observed` | P0 | o11y-kit (new module) | L (3-5d) | None |
| US-4.2 Server properties | P0 | o11y-kit-autoconfigure | S (0.5-1d) | None |
| US-4.3 Documentation | P1 | o11y-kit/README | S (1d TW) | US-4.1, US-4.2 |

**Total:** ~6-8 person-days

**Dependency graph:**
```
US-4.1 (no deps) ──────┐
                        ├── US-4.3 (needs both features stable)
US-4.2 (no deps) ──────┘
```

US-4.1 and US-4.2 are independent and can be developed in parallel.