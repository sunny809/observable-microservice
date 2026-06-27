# Technical Design — Sprint N

**Author:** Tech-Lead
**Date:** YYYY-MM-DD
**Status:** Draft / Review / Approved

---

## 1. Summary

Brief description of what this design covers.

## 2. Related User Stories

- [US-N.1](../sprints/sprint-N/1-backlog.md) — Feature description
- [US-N.2](../sprints/sprint-N/1-backlog.md) — Feature description

## 3. Scope of Change

**Modules affected:**
- `module-a/` — description of changes
- `module-b/` — description of changes

**Files to change:**
| File | Change Type | Description |
|------|-------------|-------------|
| `path/to/Interface.java` | Modify | Add new method overload |
| `path/to/Impl.java` | Modify | Implement new method |
| `path/to/Class.java` | New | New utility class |

## 4. Architecture Decision

| Option | Pros | Cons | Selected? |
|--------|------|------|-----------|
| Option A | ... | ... | **✓** |
| Option B | ... | ... | ✗ |

**Rationale:** Why this approach was chosen.

**Alternatives considered and rejected:**
- Option B: Rejected because ...
- Option C: Rejected because ...

## 5. Interface / API Changes

### New / Modified Method Signatures

```java
// New overload
void recordStep(String orderId, String step, String detail,
                long durationMs, String outcome);
```

### Remove / Deprecate

```java
@Deprecated(since = "v0.x.x")
void oldMethod();  // replaced by newMethod()
```

## 6. Data Flow

```
[Client] → [Component A] → [Component B] → [External Service]
                │
                ▼
          [Metrics / Logs]
```

**Key paths:**
1. Normal flow: A → B → C
2. Error flow: A → B → Compensation → DB
3. Async flow: A → Event → Listener → C

## 7. Compatibility & Migration

- **Backward compatible?** Yes / No
- **Migration required?** Yes / No — describe steps if yes
- **Configuration changes?** List new/removed/changed properties

## 8. Testing Strategy

| Level | Scope | Approach |
|-------|-------|----------|
| Unit | Method-level tests | Mock dependencies, verify metrics recording |
| Integration | Component-level | Spring Boot test with H2 / MockMvc |
| BDD | End-to-end | Cucumber + WireMock scenario updates |

## 9. Risks & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| ... | Low/Med/High | Low/Med/High | ... |