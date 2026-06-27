# Documentation Index

This directory follows a **sprint-based documentation framework**.
Each sprint produces a consistent set of artifacts with defined inputs/outputs per role.

---

## Quick Navigation

| Path | What |
|------|------|
| [`IMPLEMENTATION_GUIDE.md`](IMPLEMENTATION_GUIDE.md) | Developer guide: architecture, request flow, patterns, extension cookbook |
| [`process/sprint-lifecycle.md`](process/sprint-lifecycle.md) | Sprint lifecycle phases, RACI matrix, role definitions |
| [`templates/`](templates/) | Standard templates for each sprint artifact |
| [`sprints/`](sprints/) | Per-sprint artifacts organized by sprint number |
| [`reference/`](reference/) | Cross-sprint reference docs (glossary, etc.) |
| [`ROADMAP.md`](ROADMAP.md) | Release roadmap across o11y-kit + order-demo |
| [`SPEC.md`](SPEC.md) | o11y-kit SDK specification |
| [`LESSONS_LEARNED.md`](LESSONS_LEARNED.md) | Engineering lessons and trade-off decisions |
| [`MIGRATION.md`](MIGRATION.md) | Version migration guides |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | Common issues and fixes |

---

## Sprint Document Flow

```
                        ┌─────────────────────┐
                        │  1-backlog.md        │  ← PO: User Stories + AC
                        │  (Sprint Goal)       │
                        └──────────┬──────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │                              │
                    ▼                              ▼
        ┌─────────────────────┐      ┌─────────────────────┐
        │ 2-design.md         │      │ 2-test-design.md    │  ← TL + QA 并行
        │ (Tech Design)       │      │ (Test Scenarios)    │
        └──────────┬──────────┘      └──────────┬──────────┘
                   │                             │
                   └──────────┬──────────────────┘
                              │
                              ▼
        ┌──────────────────────────────────────┐
        │ 3. Development + Code Review         │  ← Dev + TL
        │ 3-code-review-report.md              │
        └──────────────────┬───────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │ 4. Testing                           │  ← QA
        │ 4-test-report.md                     │
        └──────────────────┬───────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │ 5. Documentation + DoD Sign-off      │  ← TW + PO + SM + TL
        │  ├── 7-user-docs.md (TW)             │
        │  └── 5-dod.md (PO+SM+TL)            │
        └──────────────────┬───────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────┐
        │ 6. Review + Retro                    │  ← SM: Retrospective
        │ 6-retro.md                           │
        └──────────────────────────────────────┘
```
---

## Role Quick Reference

| Role | Plan | Design | Dev+Review | Test | Docs | DoD | Retro |
|------|:----:|:------:|:----------:|:----:|:----:|:---:|:-----:|
| **PO** | A | — | — | — | C | C | A |
| **SM** | R | — | — | — | — | R | A |
| **Tech-Lead** | C | A | A | I | C | A | C |
| **Developer** | I | R | R | C | I | I | R |
| **QA** | C | A | — | A | C | C | R |
| **Technical Writer** | I | C | — | — | A | C | R |

> A=审批, R=执行, C=咨询, I=知情.
> See full RACI: [`process/sprint-lifecycle.md`](process/sprint-lifecycle.md)

---

## Current Sprint

| Sprint | Version | Status | Backlog |
|--------|---------|--------|---------|
| Sprint 4 | v0.4.0-beta | Current | [`sprints/sprint-4/1-backlog.md`](sprints/sprint-4/1-backlog.md) |
| Sprint 3 | v0.3.0-beta | Planning | [`sprints/sprint-3/1-backlog.md`](sprints/sprint-3/1-backlog.md) |

## Completed Sprints

| Sprint | Version | Summary |
|--------|---------|---------|
| Sprint 1 | v0.1.0-alpha + v0.1.1-alpha | [`sprints/sprint-1/README.md`](sprints/sprint-1/README.md) |
| Sprint 2 | v0.2.0-alpha | [`sprints/sprint-2/README.md`](sprints/sprint-2/README.md) |