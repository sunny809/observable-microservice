# Task 1: Prometheus 抓取配置 — Report

## What was implemented

Created `docker/prometheus/prometheus.yml` with two scrape jobs:
- `order-service`: scrapes `/actuator/prometheus` from `order-demo:8080`, with `application: order-service` label
- `prometheus`: scrapes `localhost:9090` for Prometheus self-monitoring

Global config: `scrape_interval: 15s`, `evaluation_interval: 15s`

## Verification

1. YAML syntax validated via Python `yaml.safe_load()` — passed
2. Structural checks (job names, targets, labels, metrics_path) — all passed
3. Docker dry-run skipped (Docker not available in environment)

## Files changed

- Created: `docker/prometheus/prometheus.yml` (16 lines)

## Self-review findings

None. Config is minimal and matches the spec exactly.

## Commit

391926c feat(observability): add Prometheus scrape config