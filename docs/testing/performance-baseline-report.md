# Performance Baseline Report

## Metadata

- Project: FusionXPay
- Test Tool: k6
- API Gateway Base URL: `<fill>`
- Test Date: `<fill>`
- Tester: `<fill>`

## Environment

- API Gateway version/branch: `<fill>`
- Rate limiting status:
  - Phase 1: disabled
  - Phase 2: enabled
- Infrastructure notes (DB/Redis/Kafka/Eureka): `<fill>`

## Scenarios

| Scenario | Script | Target |
| --- | --- | --- |
| Admin login | `tests/performance/login.js` | p95 < 200ms |
| Order list | `tests/performance/order-list.js` | p95 < 300ms |
| Payment request | `tests/performance/payment-request.js` | p95 < 500ms |
| Concurrent login | `tests/performance/concurrent-login.js` | 100 VU, success rate > 99% |
| Order stress | `tests/performance/order-stress.js` | 50 TPS for 5 minutes, no errors |

## Phase 1: Unlimited Baseline

### Command Log

```bash
# Example
k6 run tests/performance/login.js
k6 run tests/performance/order-list.js
k6 run tests/performance/payment-request.js
k6 run tests/performance/concurrent-login.js
k6 run tests/performance/order-stress.js
```

### Results

| Scenario | p95 (ms) | Success Rate | Error Rate | 429 Count | Notes |
| --- | --- | --- | --- | --- | --- |
| Admin login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order list | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Payment request | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Concurrent login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order stress | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |

### Baseline Assessment

- Login target met: `<yes/no>`
- Order list target met: `<yes/no>`
- Payment target met: `<yes/no>`
- Concurrent login target met: `<yes/no>`
- Order stress target met: `<yes/no>`

## Phase 2: Rate-Limit Validation

### Command Log

```bash
# Example
EXPECT_429=true k6 run tests/performance/login.js
EXPECT_429=true k6 run tests/performance/order-list.js
EXPECT_429=true k6 run tests/performance/payment-request.js
EXPECT_429=true k6 run tests/performance/concurrent-login.js
EXPECT_429=true k6 run tests/performance/order-stress.js
```

### Results

| Scenario | p95 (ms) | Success Rate | Error Rate | 429 Count | Notes |
| --- | --- | --- | --- | --- | --- |
| Admin login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order list | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Payment request | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Concurrent login | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |
| Order stress | `<fill>` | `<fill>` | `<fill>` | `<fill>` | `<fill>` |

### Rate-Limit Validation

- 429 observed on login endpoint (`/api/v1/admin/auth/login`): `<yes/no>`
- 429 observed on payment endpoint (`/api/v1/payment/**`): `<yes/no>`
- 429 observed on orders endpoint (`/api/v1/orders/**`): `<yes/no>`

## Unlimited vs Limited Comparison

| Scenario | Phase 1 p95 (ms) | Phase 2 p95 (ms) | Delta (ms) | Delta (%) | Observations |
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
