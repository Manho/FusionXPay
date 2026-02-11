# FusionXPay Manual Test Checklist (Phase 3.5 / A2)

## Scope
- Real-environment manual validation for core payment flows, admin operations, RBAC isolation, and auth boundaries.
- This checklist is designed for post-deploy verification after A1 CORS update is merged and deployed.

## Execution Metadata
- Environment: `____________________`
- Base URL: `____________________`
- Build / Commit SHA: `____________________`
- Tester: `____________________`
- Execution Date: `____________________`

## Shared Preconditions
- Admin credentials available for `POST /api/v1/admin/auth/login`.
- At least two merchant accounts available (Merchant-A, Merchant-B).
- API keys available (or generated via `POST /api/v1/auth/register`).
- Payment provider sandbox credentials configured (Stripe/PayPal).
- Kafka and dependent services healthy.

## Result Legend
- `[ ] PASS`
- `[ ] FAIL`
- Defect ID (if failed): `____________________`

---

## A1-CORS-001: Browser CORS Verification (Post-Deploy)

### Preconditions
- `CORS_ALLOWED_ORIGINS` includes deployed frontend domain and localhost origins.
- Frontend domain loaded in browser.

### Steps
1. Open frontend app from production domain.
2. Trigger admin login request from browser.
3. Check Network tab response headers and browser console.

### Expected Result
- No CORS error in browser console.
- Response includes `Access-Control-Allow-Origin` for request origin.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-PAY-001: End-to-End Payment Flow (Stripe)

### Preconditions
- Valid API key and order payload prepared.
- Stripe sandbox configured.

### Steps
1. Create order via `POST /api/v1/orders`.
2. Initiate payment via `POST /api/v1/payment/request`.
3. Complete payment in Stripe sandbox.
4. Verify webhook callback is received.
5. Query order status via `GET /api/v1/orders/{orderNumber}`.
6. Verify notification event consumption/logs.

### Expected Result
- Payment succeeds and order status transitions to paid/processed state.
- Webhook processing is successful.
- Notification path is triggered.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-PAY-002: End-to-End Payment Flow (PayPal)

### Preconditions
- Valid API key and order payload prepared.
- PayPal sandbox configured.

### Steps
1. Create order via `POST /api/v1/orders`.
2. Initiate payment via `POST /api/v1/payment/request` with PayPal provider.
3. Complete approval on PayPal sandbox page.
4. Verify return/callback endpoints are processed.
5. Verify order and transaction status update.

### Expected Result
- Payment flow completes without manual data correction.
- Order and payment transaction reflect final successful state.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-REFUND-001: Refund Flow Validation

### Preconditions
- Existing successful payment transaction available.

### Steps
1. Submit refund request via `POST /api/v1/payment/refund`.
2. Verify provider callback/webhook processing.
3. Verify refund status in payment record.
4. Verify related order status update.

### Expected Result
- Refund request succeeds and downstream status is consistent.
- No duplicate or stuck transaction state.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-ADMIN-001: Admin Login and Orders Browse

### Preconditions
- Admin account exists.

### Steps
1. Login via `POST /api/v1/admin/auth/login`.
2. Open orders list in admin dashboard.
3. Apply order status/order number filters.
4. Open one order detail page.

### Expected Result
- Login success with valid JWT.
- List/filter/detail endpoints return expected data and UI renders correctly.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-RBAC-001: Merchant Isolation

### Preconditions
- Merchant-A and Merchant-B both have orders.
- API keys for both merchants available.

### Steps
1. Query orders with Merchant-A API key.
2. Verify Merchant-B orders are not returned.
3. Attempt direct access to Merchant-B order (if endpoint supports direct fetch).

### Expected Result
- Merchant-A can only access its own orders.
- Cross-merchant access is denied or filtered out.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-RBAC-002: Admin Visibility

### Preconditions
- Admin JWT token available.

### Steps
1. Query admin orders endpoint.
2. Verify orders from multiple merchants are visible.

### Expected Result
- Admin can view all merchants' orders according to role design.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-AUTH-001: JWT Boundary Checks

### Preconditions
- Protected admin endpoint identified.

### Steps
1. Call protected endpoint without JWT.
2. Call protected endpoint with expired JWT.
3. Call protected endpoint with tampered JWT.

### Expected Result
- Unauthorized requests are rejected (401).
- No sensitive stack trace in response payload.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## A2-AUTH-002: API Key Boundary Checks

### Preconditions
- Protected public API endpoint identified (orders/payment).

### Steps
1. Call endpoint without `X-API-Key`.
2. Call endpoint with invalid `X-API-Key`.
3. Call endpoint with valid key and confirm success path.

### Expected Result
- Missing/invalid API key requests are rejected (401/403 as designed).
- Valid key is accepted.

### Actual Result
- `____________________________________________________________`

### Status
- [ ] PASS
- [ ] FAIL

---

## Defect Log

| Defect ID | Test Case | Summary | Severity | Status | Fix PR |
|-----------|-----------|---------|----------|--------|--------|
|           |           |         |          |        |        |
