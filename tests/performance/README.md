# k6 Performance Baseline

This folder contains k6 scripts for FusionXPay API Gateway performance testing.

## Prerequisites

1. Start FusionXPay services and confirm API Gateway is reachable.
2. Install k6:

```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update && sudo apt install k6
```

## Environment Variables

Common variables:

- `BASE_URL` (default: `http://localhost:8080`)
- `API_GATEWAY_PORT` (used when `BASE_URL` is not provided)
- `HTTP_TIMEOUT` (default: `30s`)
- `SUMMARY_FILE` (optional custom summary output path)
- `EXPECT_429` (`false` for baseline, `true` for rate-limit validation)

Per-script tuning variables:

- `LOGIN_VUS`, `LOGIN_DURATION`, `LOGIN_P95_TARGET_MS`
- `ORDER_LIST_VUS`, `ORDER_LIST_DURATION`, `ORDER_LIST_P95_TARGET_MS`
- `PAYMENT_VUS`, `PAYMENT_DURATION`, `PAYMENT_P95_TARGET_MS`, `PAYMENT_ORDER_POOL_SIZE`, `PAYMENT_CHANNEL`
- `CONCURRENT_LOGIN_VUS`, `CONCURRENT_LOGIN_DURATION`
- `ORDER_STRESS_TPS`, `ORDER_STRESS_DURATION`, `ORDER_STRESS_PRE_ALLOCATED_VUS`, `ORDER_STRESS_MAX_VUS`

## Test Data Setup

Each script runs `setup()` and automatically performs:

1. Register API user (`POST /api/v1/auth/register`)
2. Register admin merchant (`POST /api/v1/admin/auth/register`)
3. Admin login (`POST /api/v1/admin/auth/login`)
4. Create initial order(s) (`POST /api/v1/orders`)

No manual seed data is required.

## Phase 1: Unlimited Baseline

Run against environment without rate limiting enabled:

```bash
mkdir -p tests/performance/results

k6 run tests/performance/login.js
k6 run tests/performance/order-list.js
k6 run tests/performance/payment-request.js
k6 run tests/performance/concurrent-login.js
k6 run tests/performance/order-stress.js
```

Expected baseline targets:

- Login p95 `< 200ms`
- Order list p95 `< 300ms`
- Payment request p95 `< 500ms`
- Concurrent login success rate `> 99%`
- Order stress `50 TPS` for `5m` with no errors

## Phase 2: Rate-Limit Validation

Run against environment with gateway rate limiting enabled:

```bash
mkdir -p tests/performance/results

EXPECT_429=true k6 run tests/performance/login.js
EXPECT_429=true k6 run tests/performance/order-list.js
EXPECT_429=true k6 run tests/performance/payment-request.js
EXPECT_429=true k6 run tests/performance/concurrent-login.js
EXPECT_429=true k6 run tests/performance/order-stress.js
```

`EXPECT_429=true` updates checks/thresholds so 429 responses are treated as expected behavior and counted in `http_429_count`.

## Result Files

By default each script writes a summary JSON:

- `tests/performance/results/login-summary.json`
- `tests/performance/results/order-list-summary.json`
- `tests/performance/results/payment-request-summary.json`
- `tests/performance/results/concurrent-login-summary.json`
- `tests/performance/results/order-stress-summary.json`

Use these summaries to fill `docs/testing/performance-baseline-report.md`.
