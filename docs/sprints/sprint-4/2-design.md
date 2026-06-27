# Technical Design — Sprint 4: o11y-kit API Maturity

**Author:** Tech-Lead
**Date:** 2026-06-23
**Status:** Approved

---

## 1. Summary

Sprint 4 completes two gaps in the o11y-kit SDK:
1. **Developer-declared observation**: the `@Observed` annotation lets users observe any method, not just HTTP endpoints
2. **Server configuration**: the `O11yKitProperties.Server` namespace (empty placeholder since Sprint 2) gets bound fields

## 2. Related User Stories

- [US-4.1](1-backlog.md) — `@Observed` annotation
- [US-4.2](1-backlog.md) — `o11y.kit.server.*` properties
- [US-4.3](1-backlog.md) — Documentation

## 3. Scope of Change

**Modules affected:**

| Module | Changes | US |
|--------|---------|----|
| `o11y-kit-spring-aop` (new) | New module with `@Observed`, `ObservedAspect`, `ObservedAutoConfiguration` | US-4.1 |
| `o11y-kit-spring-boot-autoconfigure` | `O11yKitProperties.Server` fields; `ObservationWebMvcAutoConfiguration` wiring | US-4.2 |
| `o11y-kit-spring-boot-starter` | Add `o11y-kit-spring-aop` dependency | US-4.1 |
| `o11y-kit/pom.xml` | Add `o11y-kit-spring-aop` module | US-4.1 |

## 4. Architecture Decision

### Decision: @Observed creates its own metric namespace

**Choice:** `@Observed` produces `o11y.observed.duration` (separate from `o11y.server.requests`).

**Rationale:** HTTP auto-instrumentation tracks endpoint-level round-trips. `@Observed` tracks method-level execution. These are different concerns with different cardinality and tag structures. Keeping them separate prevents tag pollution on the HTTP metrics.

### Decision: Server config uses @ConditionalOnProperty

**Choice:** `ObservationWebMvcAutoConfiguration` is gated by `@ConditionalOnProperty(prefix = "o11y.kit.server", name = "enabled", matchIfMissing = true)`.

**Rationale:** Consistent with the existing `HttpClientObservationAutoConfiguration` pattern. When `o11y.kit.server.enabled=false`, the entire auto-configuration class is skipped, preventing any server-side beans from being created.

## 5. Interface / API Changes

### New module: o11y-kit-spring-aop

```
o11y-kit-spring-aop/
├── pom.xml
└── src/main/java/io/o11y/kit/spring/aop/
    ├── Observed.java              (annotation)
    ├── ObservedAspect.java        (@Around aspect)
    ├── ObservedAutoConfiguration.java
    └── package-info.java
```

### New property bindings

```yaml
o11y:
  kit:
    server:
      enabled: true                    # master switch
      exclude-patterns:                # URL patterns to skip
        - /actuator/**
        - /health/**
      metrics:
        enabled: true                  # timer/counter emission
```

### New metric

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `o11y.observed.duration` | Timer | `class`, `method`, `tags`*, `outcome` | Duration of `@Observed`-annotated methods |

## 6. Compatibility & Migration

- **Backward compatible?** Yes
- **Migration required?** No — server properties default to the same hardcoded values used in v0.2.0
- **Configuration deprecations?** None

## 7. Testing Strategy

| Level | Scope | Approach |
|-------|-------|----------|
| Unit | ObservedAspect | Mock `ProceedingJoinPoint`, verify timer recording |
| Unit | O11yKitProperties | `ApplicationContextRunner` with property values |
| Integration | Full context | Verify beans created under correct conditions |
| Integration | Server config disable | Verify `ObservationWebMvcAutoConfiguration` skips when disabled |