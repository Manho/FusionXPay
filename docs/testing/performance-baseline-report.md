# Performance Baseline Report (Template)

## Metadata

- Project: FusionXPay
- Test Tool: k6
- API Gateway Base URL (Phase 1): `<fill>`
- API Gateway Base URL (Phase 2): `<fill>`
- Test Date: `<fill>`
- Tester: `<fill>`
- API Gateway version/branch: `<fill>`

> Note: This repository keeps the **methodology, scripts, and reporting template** public.
> Runtime k6 result artifacts under `tests/performance/results/` are intentionally ignored via `.gitignore`.

## Environment

- Deployment profile: `<fill>` (e.g. docker-compose.always-on.yml)
- Host resources: `<fill>` (CPU/RAM)
- DB/Redis/Kafka/Eureka: `<fill>`
- Rate limiting:
  - Phase 1: disabled (baseline)
  - Phase 2: enabled (validation)

## Scenarios

| Scenario | Script | Target |
| --- | --- | --- |
| Admin login | `tests/performance/login.js` | p95 < 200ms |
| Order list | `tests/performance/order-list.js` | p95 < 300ms |
| Payment request | `tests/performance/payment-request.js` | p95 < 500ms |
| Concurrent login | `tests/performance/concurrent-login.js` | 100 VU, success rate > 99% |
| Order stress | `tests/performance/order-stress.js` | 50 TPS for 5 minutes, no errors |

## How To Run

Create a results folder:

```bash
mkdir -p tests/performance/results
```

Phase 1 (baseline, expect no 429):

```bash
BASE_URL=<phase1_base_url> SUMMARY_FILE=tests/performance/results/login-summary.json k6 run tests/performance/login.js
BASE_URL=<phase1_base_url> SUMMARY_FILE=tests/performance/results/order-list-summary.json k6 run tests/performance/order-list.js
BASE_URL=<phase1_base_url> SUMMARY_FILE=tests/performance/results/payment-request-summary.json k6 run tests/performance/payment-request.js
BASE_URL=<phase1_base_url> SUMMARY_FILE=tests/performance/results/concurrent-login-summary.json k6 run tests/performance/concurrent-login.js
BASE_URL=<phase1_base_url> SUMMARY_FILE=tests/performance/results/order-stress-summary.json k6 run tests/performance/order-stress.js
```

Phase 2 (rate limiting validation, expect some 429):

```bash
BASE_URL=<phase2_base_url> EXPECT_429=true SUMMARY_FILE=tests/performance/results/login-summary.json k6 run tests/performance/login.js
BASE_URL=<phase2_base_url> EXPECT_429=true SUMMARY_FILE=tests/performance/results/order-list-summary.json k6 run tests/performance/order-list.js
BASE_URL=<phase2_base_url> EXPECT_429=true SUMMARY_FILE=tests/performance/results/payment-request-summary.json k6 run tests/performance/payment-request.js
BASE_URL=<phase2_base_url> EXPECT_429=true SUMMARY_FILE=tests/performance/results/concurrent-login-summary.json k6 run tests/performance/concurrent-login.js
BASE_URL=<phase2_base_url> EXPECT_429=true SUMMARY_FILE=tests/performance/results/order-stress-summary.json k6 run tests/performance/order-stress.js
```

## Phase 1: Unlimited Baseline Results

| Scenario | p95 (ms) | Success Rate | Error Rate | 429 Count | Notes |
| --- | --- | --- | --- | --- | --- |
| Admin login | `<fill>` | `<fill>` | `<fill>` | `0` | `<fill>` |
| Order list | `<fill>` | `<fill>` | `<fill>` | `0` | `<fill>` |
| Payment request | `<fill>` | `<fill>` | `<fill>` | `0` | `<fill>` |
| Concurrent login | `<fill>` | `<fill>` | `<fill>` | `0` | `<fill>` |
| Order stress | `<fill>` | `<fill>` | `<fill>` | `0` | `<fill>` |

## Phase 2: Rate-Limit Validation Results

| Scenario | p95 (ms) | Success Rate | Error Rate | 429 Count | Notes |
| --- | --- | --- | --- | --- | --- |
| Admin login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order list | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Payment request | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Concurrent login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order stress | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |

## Conclusions

- Overall baseline quality: `<fill>`
- Rate limiting impact summary: `<fill>`
- Risks and follow-up actions: `<fill>`

