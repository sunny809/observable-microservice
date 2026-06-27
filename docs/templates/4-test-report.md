# Test Report — Sprint N

**Author:** QA
**Date:** YYYY-MM-DD
**Status:** Draft / Final

---

## Executive Summary

| Metric | Value |
|--------|-------|
| Total scenarios | N |
| Passed | N |
| Failed | N |
| Skipped | N |
| Pass rate | XX% |
| JaCoCo line coverage | XX% |

## Detailed Results

### US-N.1: [Story Title]

| # | Scenario | Type | Status | Notes |
|---|----------|------|--------|-------|
| 1 | Normal path | Positive | ✅ Pass | — |
| 2 | Error: condition | Negative | ✅ Pass | — |
| 3 | Edge: boundary | Edge | ❌ Fail | Bug #001: description |

### US-N.2: [Story Title]

...

## Regression Results

| Test Suite | Before | After | Regressions? |
|-----------|--------|-------|-------------|
| Unit tests (order-application) | 90 ✅ | 90 ✅ | None |
| Unit tests (order-adapter) | 25 ✅ | 28 ✅ | None |
| BDD (Cucumber) | 16 ✅ | 16 ✅ | None |
| ArchUnit | 8 ✅ | 8 ✅ | None |

## Bugs Found

| # | US | Severity | Description | Status |
|---|----|----------|-------------|--------|
| 001 | US-N.1 | P1 | Description | Open / Fixed |

## Coverage Report

| Module | Before | After | Threshold (80%) |
|--------|--------|-------|-----------------|
| order-application | XX% | XX% | ✅ / ❌ |
| order-adapter | XX% | XX% | ✅ / ❌ |

## Recommendations

- List any issues that should be addressed before next sprint
- List any improvements to test coverage or strategy