# User Documentation — Sprint 4: o11y-kit API Maturity

**Author:** Technical Writer
**Date:** YYYY-MM-DD
**Status:** Draft

**Target audience:** Spring Boot developers using o11y-kit

---

## 1. Feature Overview

Sprint 4 delivers two capabilities that complete the o11y-kit SDK's core feature set:

| Feature | Module | Since | User Story |
|---------|--------|-------|------------|
| `@Observed` annotation — method-level observation | `o11y-kit-spring-aop` | v0.4.0-beta | US-4.1 |
| Server configuration properties | `o11y-kit-spring-boot-autoconfigure` | v0.4.0-beta | US-4.2 |

### What Problem Does This Solve?

Before v0.4.0:
- o11y-kit automatically observed **HTTP endpoints** — you could not observe a specific service method
- Server-side observation was **always on** with hardcoded exclusion patterns — you could not configure it

After v0.4.0:
- Annotate any method with `@Observed` to get Micrometer timing
- Configure which URLs to exclude via `application.yml`

## 2. Documentation

See the following for detailed usage:

- **o11y-kit README**: [`o11y-kit/README.md`](../o11y-kit/README.md)
  - "Method-level observation" section — `@Observed` usage and examples
  - Configuration table — all `o11y.kit.*` properties with types and defaults
  - Metrics reference — `o11y.observed.duration` tag format

- **o11y-kit CHANGELOG**: [`o11y-kit/CHANGELOG.md`](../o11y-kit/CHANGELOG.md)
  - v0.4.0-beta release notes