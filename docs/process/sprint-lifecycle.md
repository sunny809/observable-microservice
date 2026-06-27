# Sprint Lifecycle & RACI

## Roles

| Role | Who | Responsibilities |
|------|-----|-----------------|
| **PO** (Product Owner) | — | Define Sprint Goal, prioritize backlog, write acceptance criteria, approve DoD |
| **SM** (Scrum Master) | — | Facilitate process, track impediments, run retro, ensure artifacts are complete |
| **Tech-Lead** | — | Technical design, effort estimation, code review, architecture decisions, DoD sign-off |
| **Developer** | — | Implement stories per design doc, write unit tests, self-check DoD |
| **QA** | — | Test design, test execution, regression verification, test report |
| **Technical Writer** | — | Produce user-facing documentation: features, usage, config reference, API spec, examples |

---

## Sprint Flow

```
┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────────────┐  ┌──────────┐
│ 1.需求 & │→│ 2.设计   │→│ 3.开发 +     │→│ 4.测试   │→│ 5.文档 +        │→│ 6.检视   │
│ 计划     │  │          │  │ Code Review  │  │          │  │ DoD 签核         │  │          │
└──────────┘  └──────────┘  └──────────────┘  └──────────┘  └──────────────────┘  └──────────┘
     │             │               │               │                │                  │
     ▼             ▼               ▼               ▼                ▼                  ▼
  1-backlog   2-design.md     代码实现       4-test-report   5-dod.md            6-retro.md
              2-test-design   (Git diff)                     7-user-docs.md
```

### Phase 1: Backlog & Planning

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Define Sprint Goal + prioritize | PO | Stakeholder requirements, roadmap | Prioritized backlog |
| Write User Stories + Acceptance Criteria | PO | Feature request | User stories with AC |
| Technical feasibility + effort estimate | Tech-Lead | User stories with AC | Effort estimates (XS/S/M/L/XL) per story |
| Commit sprint scope | SM + Team | Estimated backlog | Sprint commitment |

**Output file:** `sprints/sprint-N/1-backlog.md`

### Phase 2: Design

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Technical design (architecture, API, data flow) | Tech-Lead | 1-backlog.md | 2-design.md |
| Test design (scenarios, boundaries, edge cases) | QA | 1-backlog.md | 2-test-design.md |

**Output files:** `sprints/sprint-N/2-design.md`, `sprints/sprint-N/2-test-design.md`

### Phase 3: Development & Code Review

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Implement code per design | Developer | 2-design.md | Implementation |
| Write unit tests (≥80% JaCoCo) | Developer | 2-design.md | Tests |
| Self-check against DoD checklist | Developer | 5-dod.md template | Self-review done |
| Code review | Tech-Lead | Implementation | 3-code-review-report.md |
| Fix review findings | Developer | Review report | Fixes |

**Output file:** `sprints/sprint-N/3-code-review-report.md`

### Phase 4: Testing

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Execute test scenarios | QA | 2-test-design.md, implementation | Test results |
| Regression verification | QA | existing tests | No regression |
| Bug reporting + retest | QA | Failed scenarios | Bug list (P0/P1/P2) |

**Output file:** `sprints/sprint-N/4-test-report.md`

### Phase 5: Documentation & DoD Sign-off

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Produce user-facing docs (features, usage, params, API spec, examples) | Technical Writer | 2-design.md + completed implementation + 1-backlog.md | 7-user-docs.md + updated README / references |
| DoD checklist sign-off | PO + SM + TL + QA + TW | All artifacts | 5-dod.md |

**Output files:** `sprints/sprint-N/7-user-docs.md`, `sprints/sprint-N/5-dod.md`

### Phase 6: Review & Retro

| Activity | Owner | Input | Output |
|----------|-------|-------|--------|
| Sprint demo to stakeholders | PO + Team | Completed work | Stakeholder feedback |
| Retrospective | SM + Team | Sprint results | 6-retro.md |

**Output file:** `sprints/sprint-N/6-retro.md`

---

## RACI Matrix

| Phase | Activity | PO | SM | TL | Dev | QA | TW |
|-------|----------|:--:|:--:|:--:|:---:|:--:|:--:|
| **1. Plan** | Sprint Goal + priority | **A** | R | C | I | C | I |
| | User stories + AC | **A** | — | C | I | C | C |
| | Feasibility + estimates | C | — | **A** | R | — | — |
| **2. Design** | Technical design | — | — | **A** | R | C | C |
| | Test design | — | — | C | I | **A** | — |
| **3. Dev+Review** | Implementation | — | — | C | **A** | — | — |
| | Unit tests | — | — | C | **A** | — | — |
| | Code review | — | — | **A** | R | — | — |
| **4. Test** | Execute test scenarios | — | — | I | C | **A** | — |
| | Regression verify | — | — | I | C | **A** | — |
| **5. Docs+DoD** | User-facing documentation | C | — | C | I | C | **A** |
| | DoD sign-off | C | R | **A** | I | C | C |
| **6. Retro** | Sprint demo | **A** | R | C | R | — | R |
| | Retro + action items | C | **A** | R | R | R | R |

> **R**=执行, **A**=审批, **C**=咨询, **I**=知情

---

## Effort Estimation Scale

| Size | Effort Range | Example |
|------|-------------|---------|
| XS | < 1 hour | Add span attribute, config change |
| S | 0.5 day | Wire existing metrics, log format change |
| M | 1-2 days | New timer/metric, new port interface method |
| L | 3-5 days | New module, new adapter with tests |
| XL | 1-2 weeks | New saga step, new OTel instrumentation |

---

## Definition of Done (Global)

For every User Story, the following must be true before it can be marked DONE:

- [ ] All Acceptance Criteria confirmed passing by QA
- [ ] JaCoCo line coverage ≥80% for new/modified code
- [ ] SpotBugs + Checkstyle: no new violations
- [ ] Code Review: no open P0/P1 findings
- [ ] Regression tests: zero new failures
- [ ] Corresponding docs updated (or noted "no doc change needed")

For the Sprint as a whole:

- [ ] All stories meet their individual DoD
- [ ] **User-facing documentation produced** (Technical Writer: 7-user-docs.md)
- [ ] Target branch merged
- [ ] CHANGELOG.md updated
- [ ] Version number bumped
- [ ] Sprint artifacts committed to `docs/sprints/sprint-N/`