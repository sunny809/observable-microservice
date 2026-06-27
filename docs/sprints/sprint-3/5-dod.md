# Definition of Done — Sprint 3: v0.3.0-beta

**Checked by:** PO + SM + Tech-Lead
**Date:** YYYY-MM-DD (fill at sprint end)

---

## Per-Story DoD

### US-3.1: 修复 OrderMetrics 死代码

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-4 pass | ⏳ | Test report US-3.1 scenarios 1.1-1.4 |
| JaCoCo ≥80% (new code) | ⏳ | Coverage report |
| SpotBugs: no new violations | ⏳ | Static analysis report |
| Checkstyle: no new violations | ⏳ | Static analysis report |
| Code Review: no open P0/P1 | ⏳ | Code review report |
| Regression: zero new failures | ⏳ | `mvn verify` pass |

### US-3.2: 扩展 SagaLogPort

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-4 pass | ⏳ | Test report US-3.2 scenarios 2.1-2.4 |
| JaCoCo ≥80% (new code) | ⏳ | Coverage report |
| Code Review: no open P0/P1 | ⏳ | Code review report |
| Regression: zero new failures | ⏳ | `mvn verify` pass |

### US-3.3: 三步 saga 阶段耗时

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-4 pass | ⏳ | Test report US-3.3 scenarios 3.1-3.3 |
| JaCoCo ≥80% (new code) | ⏳ | Coverage report |
| Code Review: no open P0/P1 | ⏳ | Code review report |

### US-3.4: 异步等待间隙

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-3 pass | ⏳ | Test report US-3.4 scenarios 4.1-4.2 |
| Code Review: no open P0/P1 | ⏳ | Code review report |

### US-3.5: ECS 日志格式

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-4 pass | ⏳ | Test report US-3.5 scenarios 5.1-5.2 |
| Code Review: no open P0/P1 | ⏳ | Code review report |

### US-3.6: Per-SKU span attribute

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-3 pass | ⏳ | Test report US-3.6 scenarios 6.1-6.2 |
| Code Review: no open P0/P1 | ⏳ | Code review report |

### US-3.7: 单元测试覆盖

| Criterion | Status | Evidence |
|-----------|--------|----------|
| AC1-4 pass | ⏳ | Test report US-3.7 |
| JaCoCo ≥80% (overall) | ⏳ | Coverage report |

---

## Sprint-Level DoD

| Criterion | Status | Notes |
|-----------|--------|-------|
| All 7 stories meet per-story DoD | ⏳ | — |
| Target branch `feature/sprint3-latency` → `main` merged | ⏳ | — |
| CI pipeline (build + test + lint + scan) passes | ⏳ | Link: — |
| o11y-kit CHANGELOG.md updated with v0.3.0-beta entry | ⏳ | — |
| main project CHANGELOG.md (optional) updated | ⏳ | — |
| Version bumped: v0.2.0-alpha → v0.3.0-beta | ⏳ | — |
| Sprint artifacts committed to `docs/sprints/sprint-3/` | ⏳ | All 7 docs present |
| `docs/SPRINT_3_PLAN.md` marked as deprecated | ⏳ | Note added at top |

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| PO | — | — | — |
| Tech-Lead | — | — | — |
| SM | — | — | — |