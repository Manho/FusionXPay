# Testing Strategy (Public)

This document describes the testing approach for FusionXPay.
It focuses on **methodology** and intentionally avoids environment-specific secrets or private runtime artifacts.

## Goals

- Validate end-to-end service behavior through the API gateway
- Catch breaking changes early with repeatable checks
- Provide confidence signals for reliability, performance, and security work

## Test Layers

### 1. Unit Tests

- Scope: pure logic, DTO validation, small utilities
- Runs fast in CI and locally
- Ownership: each service

### 2. Integration Tests

- Scope: service wiring, gateway filters, request/response contracts, basic DB interactions
- Runs in CI where feasible
- Prefer deterministic setups and stable fixtures

### 3. Runtime Verification (Service Chain)

- Purpose: validate the deployed system behaves as expected (routing, auth, critical flows)
- Tooling: `scripts/verify-service-chain.sh`
- Output: PASS/WARN/FAIL summary for quick triage

### 4. Manual Validation (High-Risk User Flows)

- Purpose: cover UI/browser behavior and provider sandbox flows that are not fully automatable
- Tooling: `docs/testing/manual-test-checklist.md` (template)
- Keep provider credentials and real environment notes private

### 5. Performance Baseline

- Purpose: track trends over time and validate rate limiting behavior
- Tooling: `tests/performance/*.js` (k6)
- Reporting: `docs/testing/performance-baseline-report.md` (template)
- Runtime artifacts: stored under `tests/performance/results/` which is ignored by `.gitignore`

### 6. Security Hardening

- Purpose: ensure the project follows a consistent, reviewable hardening process
- Tooling: `docs/testing/security-baseline-report.md` (public checklist template)
- Sensitive findings and exploit details should be kept private until fixed

## Release Gate (Suggested)

- `scripts/verify-service-chain.sh` passes with no FAIL
- Manual checklist: critical flows PASS (admin login, order list, RBAC isolation)
- k6: no unexpected error spikes compared to last known baseline
- Security hardening checklist reviewed for the change scope

