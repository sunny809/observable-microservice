# Test Design — Sprint N

**Author:** QA
**Date:** YYYY-MM-DD
**Status:** Draft / Review / Approved

---

## 1. Scope

**In scope:** User Stories covered by this test design.
**Out of scope:** Explicitly excluded areas.

## 2. Test Strategy

| US | Unit Tests | Integration Tests | BDD |
|----|-----------|-------------------|-----|
| US-N.1 | ✅ | ✅ | ❌ |
| US-N.2 | ✅ | ❌ | ✅ |

## 3. Test Scenarios

### US-N.1: [Story Title]

| # | Scenario | Steps | Expected | Type |
|---|----------|-------|----------|------|
| 1 | Normal path | 1. ... 2. ... 3. ... | Expected outcome | Positive |
| 2 | Error: condition | 1. ... 2. ... | Expected error handling | Negative |
| 3 | Edge: boundary | 1. ... 2. ... | Expected behavior | Edge |

### US-N.2: [Story Title]

...

## 4. Test Data

| Data Type | Value | Purpose |
|-----------|-------|---------|
| Order ID | `ord-42` | Normal path order |
| SKU | `SKU-OUT-OF-STOCK` | Test inventory failure |

## 5. Environment Dependencies

| Dependency | Type | Config |
|-----------|------|--------|
| H2 in-memory DB | Embedded | `application-test.yml` |
| WireMock (Inventory) | Mocked | Port 8081 |
| Testcontainers (PostgreSQL) | Container | `testcontainers.properties` |

## 6. Acceptance Criteria Mapping

| US | AC | Test Scenario # | Status |
|----|----|-----------------|--------|
| US-N.1 | AC1 | Scenario 1 | ✅ Pass |
| | AC2 | Scenario 2, 3 | ⏳ Pending |
| US-N.2 | AC1 | Scenario 4 | ❌ Fail |

## 7. Regression Concerns

- Existing BDD scenarios that may be affected: `place_order.feature`, `inventory_failure.feature`
- Existing unit tests that may need updates: `OrderPlacementSagaTest.java`
- Performance impact: None expected