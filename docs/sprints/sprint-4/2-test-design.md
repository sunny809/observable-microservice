# Test Design — Sprint 4: o11y-kit API Maturity

**Author:** QA
**Date:** 2026-06-23
**Status:** Approved

---

## 1. Scope

**In scope:** US-4.1 (@Observed annotation), US-4.2 (Server properties)
**Out of scope:** Performance tests, mutation tests

## 2. Test Scenarios

### US-4.1: @Observed annotation

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 1.1 | Basic method records timer | Mock `ProceedingJoinPoint` returning value | Timer `o11y.observed.duration` count=1 | Positive |
| 1.2 | Success outcome tag | Mock success path | Timer with `outcome="success"` | Positive |
| 1.3 | Error outcome tag | Mock exception path | Timer with `outcome="error"` count=1 | Negative |
| 1.4 | Custom metric name | `@Observed(name="custom.name")` | Timer `custom.name` created | Positive |
| 1.5 | Custom tags | `@Observed(tags={"key","val"})` | Timer has tag `key=val` | Positive |
| 1.6 | Null registry rejected | `new ObservedAspect(null)` | Throws IllegalArgumentException | Negative |
| 1.7 | Auto-config creates bean | `ApplicationContextRunner` with `SimpleMeterRegistry` | `ObservedAspect` bean exists | Integration |

### US-4.2: Server properties

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 2.1 | Default enabled | Load context with defaults | `ServerObservationHandler` bean exists | Integration |
| 2.2 | Disabled via config | `o11y.kit.server.enabled=false` | `ServerObservationHandler` bean absent | Integration |
| 2.3 | Custom exclude pattern | Set exclude pattern | `ObservationWebMvcAutoConfiguration` uses it | Integration |
| 2.4 | Default exclude patterns match v0.2.0 | No config override | Defaults = `/actuator/**`, `/health/**` | Integration |
| 2.5 | Metrics disabled | `o11y.kit.server.metrics.enabled=false` | Handler installed but metrics suppressed | Integration |

## 3. Acceptance Criteria Mapping

| US | AC | Test Scenario # |
|----|----|-----------------|
| US-4.1 | AC1 | 1.1 |
| | AC2 | 1.4 |
| | AC3 | 1.5 |
| | AC4 | 1.3 |
| | AC5 | 1.7 |
| | AC6 | 1.1 (verify existing metrics unaffected) |
| US-4.2 | AC1 | 2.2 |
| | AC2 | 2.3 |
| | AC3 | 2.4 |
| | AC4 | 2.5 |