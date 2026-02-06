# FusionXPay Product Requirements Document (PRD)

**Version:** 2.1
**Last Updated:** 2026-02-06
**Status:** Living Document

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [User Personas](#2-user-personas)
3. [User Stories & Acceptance Criteria](#3-user-stories--acceptance-criteria)
4. [Functional Requirements](#4-functional-requirements)
5. [API Specification](#5-api-specification)
6. [Non-Functional Requirements](#6-non-functional-requirements)
7. [Out of Scope](#7-out-of-scope)
8. [Glossary](#8-glossary)
9. [Traceability to Roadmap Phases](#9-traceability-to-roadmap-phases)

---

## 1. Introduction

### 1.1 Product Overview

**FusionXPay** is an enterprise-grade, open-source payment gateway platform that unifies multiple payment providers (Stripe, PayPal, and more) into a single, elegant RESTful API. Built on a cloud-native microservices architecture, it enables businesses to process payments, manage transactions, and scale their payment infrastructure with minimal integration effort.

### 1.2 Vision

To become the go-to open-source payment aggregation platform that empowers developers and businesses to integrate multiple payment providers through one unified API, reducing complexity and accelerating time-to-market.

### 1.3 Current State vs Target State

#### Current State (Implemented MVP)

| Component | Status |
|-----------|--------|
| Payment core (Stripe/PayPal) | ✅ Implemented |
| Refund and webhook flows | ✅ Implemented |
| Merchant admin MVP (auth, order query, RBAC) | ✅ Implemented |
| Multi-service architecture (5 services + common) | ✅ Implemented |
| Java 17 runtime | ✅ Current (target: Java 21 in Phase 1) |
| API routes at `/api/*` (non-versioned) | ✅ Current |
| Kafka-based event communication | ✅ Implemented |

#### Target State (Roadmap: Phase 1-4)

| Component | Target |
|-----------|--------|
| Full PRD coverage (payment, admin, landing, docs, SDK) | Phase 1-4 |
| `/api/v1` as canonical API namespace | Phase 1 |
| End-to-end quality gates (CI/CD, testing) | Phase 2 |
| Cloud deployment (Vercel + Railway) | Phase 3 |
| Landing pages + documentation site | Phase 3 |
| TypeScript + Java SDKs | Phase 4 |
| Trial-based commercialization | Phase 4 |

#### Key Gaps

| Gap | Impact | Resolution Phase |
|-----|--------|------------------|
| API routes not versioned | Breaking changes risk | Phase 1 |
| No CI/CD pipelines | Manual testing burden | Phase 2 |
| No cloud deployment | Not publicly accessible | Phase 3 |
| No SDK | Higher integration friction | Phase 4 |
| Incomplete PRD | Unclear scope | Phase 1 |

### 1.4 Business Goals

| Goal | Description | Success Metric |
|------|-------------|----------------|
| **Developer Adoption** | Attract developers to integrate FusionXPay | 100+ GitHub stars, 50+ npm/Maven downloads in first 6 months |
| **Payment Processing** | Enable reliable payment processing | 99.9% uptime, <500ms average latency |
| **Multi-Provider Support** | Support major payment providers | Stripe + PayPal initially, extensible architecture |
| **Merchant Enablement** | Provide self-service merchant tools | Self-registration, API key management, order dashboard |

### 1.5 Project Scope

This PRD covers the complete FusionXPay platform across **4 development phases** (3-4 months):

| Phase | Focus | Duration |
|-------|-------|----------|
| **Phase 1** | PRD Rewrite + Baseline + API Versioning | Month 1 |
| **Phase 2** | Automated Testing + CI/CD | Month 2 |
| **Phase 3** | Cloud Deployment + Landing Page + Documentation | Month 3 |
| **Phase 4** | SDK Delivery + Commercialization Preparation | Month 4 |

### 1.6 Two-Project Architecture

FusionXPay consists of two repositories:

| Repository | Description | Tech Stack |
|------------|-------------|------------|
| **FusionXPay** (Backend) | Microservices for payment processing | Java 17 (target: 21), Spring Boot 3.2, Kafka, MySQL, Redis |
| **fusionxpay-web** (Frontend) | Admin dashboard & landing pages | Next.js 14, TypeScript, Tailwind CSS, shadcn/ui |

### 1.7 Assumptions and Constraints

**Assumptions:**
- Stripe and PayPal are the only guaranteed providers in this roadmap cycle.
- Single-region deployment is sufficient for MVP.
- Message queue infrastructure (Kafka) can handle expected transaction volume.

**Constraints:**
- PCI-DSS certification is out of scope; architecture must remain PCI-ready.
- Backward-compatibility window for legacy routes is limited to two minor SDK releases after `/api/v1` launch.
- Full paid billing integration is deferred (trial-first approach).

---

## 2. User Personas

### 2.1 Merchant Developer

**Profile:** A software developer integrating payment functionality into their application.

| Attribute | Description |
|-----------|-------------|
| **Goals** | Quick integration, clear documentation, reliable API |
| **Pain Points** | Complex multi-provider integration, inconsistent APIs |
| **Needs** | SDK, API docs, sandbox environment, webhook examples |
| **Technical Level** | High |

**Key Jobs to Be Done:**
- Integrate payment processing into my application
- Handle webhook callbacks securely
- Test payments in sandbox before going live
- Debug failed transactions

### 2.2 Merchant Admin

**Profile:** A business user managing payment operations for their company.

| Attribute | Description |
|-----------|-------------|
| **Goals** | Monitor transactions, track revenue, manage refunds |
| **Pain Points** | Lack of visibility into payment status, manual reconciliation |
| **Needs** | Dashboard, order search/filter, export functionality |
| **Technical Level** | Low to Medium |

**Key Jobs to Be Done:**
- View all orders and their payment status
- Search for specific transactions
- Process refunds when needed
- Export transaction data for accounting

### 2.3 Platform Admin

**Profile:** An administrator managing the FusionXPay platform itself.

| Attribute | Description |
|-----------|-------------|
| **Goals** | Manage all merchants, monitor platform health |
| **Pain Points** | Need visibility across all merchants |
| **Needs** | Cross-merchant order view, system monitoring, deployment safety |
| **Technical Level** | Medium to High |

**Key Jobs to Be Done:**
- View orders across all merchants
- Manage merchant accounts
- Monitor system health and performance
- Control rollout and rollback

---

## 3. User Stories & Acceptance Criteria

### 3.1 Payment Core

#### US-PAY-001: Create Order

**As a** Merchant Developer,
**I want to** create an order via API,
**So that** payment is decoupled from order lifecycle.

**Acceptance Criteria:**
- Given valid merchant credentials and order payload, when calling `POST /api/v1/orders`, then the API returns a persistent order with status `NEW`.
- Given an invalid payload, when calling the same endpoint, then the API returns standardized validation errors with HTTP 400.
- (Phase 2+) Given a duplicate request with same idempotency key, when calling the endpoint, then the same order is returned without creating a duplicate.

#### US-PAY-002: Initiate Payment

**As a** Merchant Developer,
**I want to** initiate payment for an existing order,
**So that** my customer can complete the payment through their preferred provider.

**Acceptance Criteria:**
- Given an existing order in `NEW` state, when calling `POST /api/v1/payment/request`, then payment initiation starts and order transitions to `PROCESSING`.
- Given a valid provider selection (Stripe/PayPal), when payment is initiated, then a provider-specific payment URL is returned for customer redirect.
- Given provider-side failure, when initiation is attempted, then failure is logged and a normalized error is returned.
- Given duplicate payment attempt for same order, when called, then idempotency rules prevent duplicate transactions.

#### US-PAY-003: Handle Provider Webhooks

**As** FusionXPay,
**I want to** process asynchronous provider callbacks securely,
**So that** order status is updated accurately.

**Acceptance Criteria:**
- Given a signed valid Stripe webhook, when callback is received, then signature verification passes and order status updates to `SUCCESS` or `FAILED`.
- Given a valid PayPal webhook with proper headers, when callback is received, then header verification passes and status is updated.
- Given duplicate or replay callback, when callback is received repeatedly, then idempotency rules prevent duplicate side effects.
- Given all webhook events, when processed, then events are logged for audit with correlation ID.

#### US-PAY-004: Process Refund

**As a** Merchant Developer,
**I want to** initiate a refund via API,
**So that** I can return funds to my customer.

**Acceptance Criteria:**
- Given an eligible transaction in `SUCCESS` state, when calling `POST /api/v1/payment/refund`, then refund lifecycle starts and status can be queried.
- Given a partial refund amount, when refund is requested, then partial refund is processed and order status updates to `PARTIALLY_REFUNDED`.
- Given a full refund amount, when refund is requested, then full refund is processed and order status updates to `REFUNDED`.
- Given ineligible transaction state, when refund is requested, then API rejects with deterministic business error code.

### 3.2 Merchant Admin Dashboard

#### US-ADM-001: Merchant Login

**As a** Merchant Admin,
**I want to** log in to the dashboard securely,
**So that** I can access my order data.

**Acceptance Criteria:**
- Given valid credentials (email + password), when logging in via `POST /api/v1/admin/auth/login`, then JWT token is issued with role claims.
- Given invalid credentials, when logging in, then request is denied with clear error message.
- Given expired token, when accessing protected endpoint, then request is denied with 401 status.

#### US-ADM-002: View Orders

**As a** Merchant Admin,
**I want to** view my orders in a paginated list,
**So that** I can monitor my transaction activity.

**Acceptance Criteria:**
- Given an authenticated merchant with `MERCHANT` role, when querying orders, then only their own orders are returned.
- Given an authenticated user with `ADMIN` role, when querying orders, then all merchants' orders are returned.
- Given pagination parameters, when querying orders, then paginated result is returned with consistent metadata (page, size, total).

#### US-ADM-003: Filter and Search Orders

**As a** Merchant Admin,
**I want to** filter and search orders,
**So that** I can find specific transactions quickly.

**Acceptance Criteria:**
- Given status filter parameter, when querying orders, then only orders with matching status are returned.
- Given date range parameters, when querying orders, then only orders within range are returned.
- Given order number search, when querying orders, then matching order is returned.
- Given multiple filters, when querying orders, then filters are applied with AND logic.

#### US-ADM-004: View Order Details

**As a** Merchant Admin,
**I want to** view detailed information for a single order,
**So that** I can investigate transaction issues.

**Acceptance Criteria:**
- Given an existing order ID, when requesting details via `GET /api/v1/admin/orders/{id}`, then order and related payment state are returned.
- Given a non-existent order ID, when requesting details, then 404 is returned.
- Given an order belonging to different merchant, when accessed by `MERCHANT` role, then access is denied.

#### US-ADM-005: Export Orders (Phase 4)

**As a** Merchant Admin,
**I want to** export orders to CSV,
**So that** I can perform offline analysis or accounting.

**Acceptance Criteria:**
- Given filtered order results, when export is requested, then CSV file is downloaded.
- Given large result set, when export is requested, then export is processed asynchronously with download link.

### 3.3 Landing Page & Marketing (Phase 3)

#### US-MKT-001: Landing Page

**As a** potential user,
**I want to** understand what FusionXPay offers,
**So that** I can decide whether to use it.

**Acceptance Criteria:**
- Given a public visitor, when visiting landing root, then hero section with clear value proposition is visible.
- Given a public visitor, when scrolling, then features section highlighting key capabilities is visible.
- Given a mobile device, when visiting landing page, then responsive design is applied.
- Given Lighthouse audit, when measuring performance, then score is > 90.

#### US-MKT-002: Pricing Page (Phase 4)

**As a** potential user,
**I want to** understand the pricing model,
**So that** I can evaluate the cost.

**Acceptance Criteria:**
- Given a public visitor, when opening pricing page, then clear pricing tiers are displayed.
- Given pricing page, when viewing features, then feature comparison table is visible.
- Given trial-first model, when viewing pricing, then free trial is prominently called out.

#### US-MKT-003: Documentation Site (Phase 3)

**As a** developer,
**I want to** access comprehensive documentation,
**So that** I can integrate FusionXPay successfully.

**Acceptance Criteria:**
- Given published docs, when searching core flows, then endpoint, request schema, response schema, and error codes are discoverable.
- Given Quick Start guide, when following steps, then first sandbox payment can be completed in 5 minutes.
- Given webhook guide, when implementing, then signature verification is clearly documented.

### 3.4 Developer Experience

#### US-DX-001: SDK Integration (Phase 4)

**As a** developer,
**I want to** use an official SDK,
**So that** I can integrate faster with type safety.

**Acceptance Criteria:**
- Given TypeScript SDK, when installing via npm, then package is available as `fusionxpay-node`.
- Given Java SDK, when adding via Maven, then artifact is available from Maven Central.
- Given SDK docs, when following quick-start samples, then minimal payment flow can be completed in sandbox.
- Given webhook payload, when using SDK verification helper, then signature is validated correctly.

#### US-DX-002: API Versioning (Phase 1)

**As a** developer,
**I want to** use versioned API endpoints,
**So that** my integration won't break with updates.

**Acceptance Criteria:**
- Given Phase 1 completion, when calling API, then all endpoints are available under `/api/v1/*`.
- Given legacy endpoint call, when accessing old route, then `Deprecation` header is returned.
- Given deprecation period, when legacy route is used, then it remains functional for 2 minor SDK versions.

### 3.5 Commercialization (Phase 4)

#### US-BIZ-001: Merchant Self-Registration

**As a** new user,
**I want to** register for an account,
**So that** I can start using FusionXPay.

**Acceptance Criteria:**
- Given valid registration request (email, password, company), when onboarding completes, then merchant account is created.
- Given successful registration, when email is verified, then API key is auto-generated.
- Given new merchant, when first login, then welcome guide is displayed.

#### US-BIZ-002: API Key Management

**As a** Merchant Developer,
**I want to** manage my API keys,
**So that** I can rotate keys and manage access.

**Acceptance Criteria:**
- Given authenticated merchant, when viewing dashboard, then active API keys are displayed.
- Given generate key action, when triggered, then new API key is created with label.
- Given revoke key action, when triggered, then key is immediately invalidated.

#### US-BIZ-003: Free Trial

**As a** new user,
**I want to** try FusionXPay for free,
**So that** I can evaluate before committing.

**Acceptance Criteria:**
- Given new registration, when account is created, then trial period starts automatically.
- Given active trial, when viewing dashboard, then trial end date is clearly displayed.
- Given trial expiring, when approaching end date, then upgrade prompts are shown.

---

## 4. Functional Requirements

### 4.1 payment-service

**Purpose:** Process payments, handle provider integration, manage refunds.

#### Capabilities

| Capability | Description |
|------------|-------------|
| Provider Integration | Stripe Checkout Sessions, PayPal Orders API v2 |
| Webhook Processing | Signature verification (Stripe), header verification (PayPal) |
| Idempotency Control | Redis-based idempotency keys prevent duplicate processing |
| Event Publishing | Publishes payment events to Kafka for downstream consumers |
| Refund Processing | Full and partial refunds through original provider |

#### Endpoints (Target: `/api/v1`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/payment/request` | Initiate payment |
| GET | `/api/v1/payment/transaction/{id}` | Get transaction by ID |
| GET | `/api/v1/payment/order/{orderId}` | Get transaction by order |
| GET | `/api/v1/payment/providers` | List available providers |
| POST | `/api/v1/payment/refund` | Initiate refund |
| POST | `/api/v1/payment/webhook/stripe` | Stripe webhook |
| POST | `/api/v1/payment/paypal/webhook` | PayPal webhook |
| GET | `/api/v1/payment/paypal/return` | PayPal return callback |
| GET | `/api/v1/payment/paypal/cancel` | PayPal cancel callback |

### 4.2 order-service

**Purpose:** Manage order lifecycle, persist order data, handle status transitions.

#### Capabilities

| Capability | Description |
|------------|-------------|
| Order Lifecycle | State machine: NEW → PROCESSING → SUCCESS/FAILED/REFUNDED |
| Kafka Consumer | Listens to payment events and updates order status |
| Order Number | Generates human-readable order numbers (e.g., `ORD-12345678`) |
| Query Support | Pagination, filtering by status/merchantId/orderNumber |

#### Endpoints (Target: `/api/v1`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/orders` | Create order |
| GET | `/api/v1/orders` | List orders (paginated) |
| GET | `/api/v1/orders/{orderNumber}` | Get by order number |
| GET | `/api/v1/orders/id/{orderId}` | Get by order ID |

#### Order Status State Machine

```
NEW → PROCESSING → SUCCESS
                 → FAILED
                 → REFUNDED
                 → PARTIALLY_REFUNDED
```

**Transition Rules:**
- `NEW` → `PROCESSING`: On payment initiation
- `PROCESSING` → `SUCCESS`: On successful payment callback
- `PROCESSING` → `FAILED`: On failed payment callback
- `SUCCESS` → `REFUNDED`: On full refund
- `SUCCESS` → `PARTIALLY_REFUNDED`: On partial refund

### 4.3 admin-service

**Purpose:** Provide merchant authentication and role-based order access.

#### Capabilities

| Capability | Description |
|------------|-------------|
| JWT Authentication | HMAC-signed tokens with configurable expiry |
| Password Security | BCrypt hashing (default strength) |
| Role-Based Access | MERCHANT sees own orders, ADMIN sees all |

#### Endpoints (Target: `/api/v1`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/auth/login` | Merchant login |
| POST | `/api/v1/admin/auth/register` | Merchant registration |
| GET | `/api/v1/admin/auth/me` | Get current user |
| GET | `/api/v1/admin/orders` | List orders (RBAC) |
| GET | `/api/v1/admin/orders/{id}` | Get order detail (RBAC) |

#### Role-Based Access Control

| Role | Permissions |
|------|-------------|
| MERCHANT | View own orders only |
| ADMIN | View all orders across merchants |

### 4.4 notification-service

**Purpose:** Handle notification delivery to merchants.

#### Capabilities

| Capability | Description |
|------------|-------------|
| Kafka Consumer | Listens to order events and triggers notifications |
| Notification Cleanup | Scheduled cleanup of old notifications |
| Future: Webhook Delivery | Push to merchant-configured URLs (Phase 4) |

#### Endpoints (Target: `/api/v1`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/notifications` | Create notification |
| GET | `/api/v1/notifications` | List notifications |
| GET | `/api/v1/notifications/{id}` | Get notification |
| DELETE | `/api/v1/notifications/{id}` | Delete notification |

### 4.5 api-gateway

**Purpose:** Route requests, handle authentication, rate limiting.

#### Capabilities

| Capability | Description |
|------------|-------------|
| Request Routing | Route to internal services based on path |
| API Key Auth | Validates `X-API-Key` header for public API access |
| Request Logging | Logs all API requests with correlation ID |
| Future: Rate Limiting | Protect against abuse (Phase 2) |

### 4.6 billing-service (Phase 4 - Planned)

**Purpose:** Manage trial periods, usage metering, and future billing.

#### Planned Capabilities

| Capability | Description |
|------------|-------------|
| Trial Management | Track trial start/end dates per merchant |
| Usage Metering | Count transactions per merchant per month |
| Feature Flags | Enable/disable features by plan |
| Usage Dashboard | Display usage analytics for pricing decisions |

---

## 5. API Specification

### 5.1 Versioning Policy

- **Target Namespace:** `/api/v1` (Phase 1 deliverable)
- **Current State:** `/api/*` (non-versioned)
- **Migration:** Legacy routes will include `Deprecation` header during transition
- **Compatibility Window:** 2 minor SDK versions after v1 launch

### 5.2 Base URL

| Environment | URL | Status |
|-------------|-----|--------|
| Local | `http://localhost:8080/api/v1` | Phase 1 |
| Staging | `https://staging-api.fusionxpay.com/api/v1` | Phase 3 |
| Production | `https://api.fusionxpay.com/api/v1` | Phase 3 |

### 5.3 Authentication

FusionXPay uses two authentication mechanisms:

| Context | Method | Header |
|---------|--------|--------|
| Public API (merchants) | API Key | `X-API-Key: <api_key>` |
| Admin Dashboard | JWT Bearer | `Authorization: Bearer <jwt_token>` |

**API Key Format:**
```
Current:  UUID-based (e.g., 550e8400-e29b-41d4-a716-446655440000)
Phase 4:  sk_live_xxxxxxxxxxxxx (production)
          sk_test_xxxxxxxxxxxxx (sandbox)
```

**JWT Token:**
- Algorithm: HMAC (symmetric signing)
- Claims: `merchantId`, `role`, `email`
- Expiry: Configurable (default 24h)

### 5.4 Error Response Format

All APIs return consistent error structure:

```json
{
  "timestamp": "2026-02-06T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid order amount",
  "path": "/api/v1/orders",
  "traceId": "abc123"
}
```

| Field | Description |
|-------|-------------|
| `timestamp` | ISO 8601 timestamp |
| `status` | HTTP status code |
| `error` | Error category |
| `message` | Human-readable message |
| `path` | Request path |
| `traceId` | Correlation ID for debugging |

### 5.5 Core Endpoint Examples

#### Create Order

```http
POST /api/v1/orders
Content-Type: application/json
X-API-Key: sk_test_xxxxxxxxxxxxx

{
  "amount": 99.99,
  "currency": "USD",
  "userId": "user_123",
  "description": "Premium subscription",
  "metadata": {
    "product_id": "prod_abc"
  }
}
```

**Response (201 Created):**
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "orderNumber": "ORD-12345678",
  "amount": 99.99,
  "currency": "USD",
  "status": "NEW",
  "createdAt": "2026-02-06T12:00:00Z"
}
```

#### Initiate Payment

```http
POST /api/v1/payment/request
Content-Type: application/json
X-API-Key: sk_test_xxxxxxxxxxxxx

{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "provider": "stripe",
  "returnUrl": "https://yoursite.com/success",
  "cancelUrl": "https://yoursite.com/cancel"
}
```

**Response (200 OK):**
```json
{
  "transactionId": "660e8400-e29b-41d4-a716-446655440001",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "provider": "STRIPE",
  "status": "INITIATED",
  "paymentUrl": "https://checkout.stripe.com/pay/cs_xxx"
}
```

#### Initiate Refund

```http
POST /api/v1/payment/refund
Content-Type: application/json
X-API-Key: sk_test_xxxxxxxxxxxxx

{
  "transactionId": "660e8400-e29b-41d4-a716-446655440001",
  "amount": 50.00,
  "reason": "Customer request"
}
```

**Response (200 OK):**
```json
{
  "refundId": "rf_xxx",
  "transactionId": "660e8400-e29b-41d4-a716-446655440001",
  "amount": 50.00,
  "status": "SUCCESS"
}
```

### 5.6 Webhook Events

#### Payment Success

```json
{
  "event": "payment.success",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionId": "660e8400-e29b-41d4-a716-446655440001",
  "amount": 99.99,
  "currency": "USD",
  "provider": "stripe",
  "timestamp": "2026-02-06T12:05:00Z"
}
```

#### Payment Failed

```json
{
  "event": "payment.failed",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "transactionId": "660e8400-e29b-41d4-a716-446655440001",
  "error": "Card declined",
  "timestamp": "2026-02-06T12:05:00Z"
}
```

> **Note:** Full API specification is maintained in OpenAPI format. See `/v3/api-docs` for the single source of truth.

---

## 6. Non-Functional Requirements

### 6.1 Performance

| Metric | Target | Notes |
|--------|--------|-------|
| Internal API (p95) | < 300ms | Non-provider-bound read/write |
| Provider-dependent ops (p95) | < 2.5s | Excluding provider outages |
| Webhook processing | < 500ms | Acknowledge time; async processing continues |
| Dashboard page load | < 2s | Initial load with data |

### 6.2 Reliability

| Metric | Target |
|--------|--------|
| Service Availability (monthly) | 99.9% |
| Core Flow Success Rate | >= 99.5% (healthy provider conditions) |
| Data Durability | 99.999% |
| Webhook Delivery | At-least-once |

**Deployment Safety:**
- Post-deploy smoke checks are mandatory for staging/production
- Automatic rollback when post-deploy smoke fails
- Rollback-triggered release lock requires manual unlock

### 6.3 Scalability

- Horizontal scaling via container orchestration
- Stateless services (except database/cache)
- Kafka for async processing to handle load spikes
- Redis for caching and distributed locking

### 6.4 Security

| Requirement | Implementation |
|-------------|----------------|
| Transport Security | TLS 1.2+ required for all external communication |
| Public API Auth | API Key via `X-API-Key` header |
| Admin Auth | JWT (HMAC-signed) via `Authorization: Bearer` |
| API Keys | UUID-based (Phase 4 target: hashed storage with `sk_live_`/`sk_test_` prefix) |
| Webhook Validation | Stripe signature verification, PayPal header verification |
| Password Storage | BCrypt (default strength) |
| SQL Injection | Parameterized queries via JPA |
| XSS Prevention | Content-Type enforcement, input sanitization |
| Secrets Management | Per-environment, never embedded in source |

### 6.5 Observability

| Component | Implementation |
|-----------|----------------|
| Structured Logging | JSON format with trace-id propagation |
| Health Checks | Spring Boot Actuator `/actuator/health` |
| Metrics | Prometheus-compatible endpoints |

**Required Alerts:**
- Payment failure rate spike (> 5% in 5 minutes)
- Webhook verification failure spike
- Orders stuck in `PROCESSING` beyond threshold (> 30 minutes)

### 6.6 Testing and Quality Gates

| Layer | Scope | Tools |
|-------|-------|-------|
| Unit | Domain logic, service contracts | JUnit 5, Mockito |
| Integration | Real middleware, mocked providers | Testcontainers, WireMock |
| Frontend Unit | Components | Vitest, React Testing Library |
| Frontend E2E | Critical flows | Playwright |

**CI Gate Triggers:**

| Trigger | Backend | Frontend | Target Time |
|---------|---------|----------|-------------|
| PR | Unit + 1 smoke integration | Lint + typecheck + Vitest + 1 Playwright | <= 15 min |
| Main/Nightly | Full unit + full integration | Full Vitest + full E2E | 20-40 min |
| Release (tag) | Full green required | Full green required | Hard gate |

### 6.7 Deployment

| Component | Platform | Status |
|-----------|----------|--------|
| Frontend | Vercel | Phase 3 |
| Backend | Railway (Docker) | Phase 3 |
| Database | Railway MySQL | Phase 3 |
| Cache | Upstash Redis | Phase 3 |
| Message Queue | Upstash Kafka | Phase 3 |

**Environment Isolation:**
- Staging and Production fully isolated
- Separate secrets/config per environment
- No shared infrastructure between environments

---

## 7. Out of Scope

The following items are **not included** in the current roadmap (Phases 1-4):

| Item | Reason |
|------|--------|
| Additional Payment Providers (Alipay, WeChat Pay) | Future expansion after MVP |
| Subscription/Recurring Payments | Requires billing infrastructure |
| Advanced Fraud Detection | Requires ML infrastructure |
| Multi-currency Conversion | Complex regulatory requirements |
| PCI-DSS Level 1 Certification | Architecture supports, formal audit deferred |
| Native Mobile SDKs (iOS/Android) | Web SDK first |
| Real-time Analytics Dashboard | Basic metrics only in Phase 4 |
| Paid Billing Integration (Stripe Billing) | Trial-first approach, billing deferred |
| Multi-region Deployment | Single region for MVP |
| Large-scale BI/Analytics Platform | Basic dashboard only |

---

## 8. Glossary

| Term | Definition |
|------|------------|
| **API Gateway** | Edge routing/security layer in front of backend services |
| **Callback** | Synchronous redirect from payment provider after user action |
| **Idempotency** | Property ensuring repeated requests produce the same result |
| **JWT** | JSON Web Token, used for stateless authentication |
| **Kafka** | Distributed message queue for async communication between services |
| **Merchant** | Business entity using FusionXPay to process payments |
| **Order** | A payment request created by a merchant |
| **Provider** | External payment processor (Stripe, PayPal) |
| **RBAC** | Role-Based Access Control |
| **Refund** | Reversal of a completed payment |
| **SLO** | Service Level Objective, measurable reliability target |
| **Testcontainers** | Integration testing with disposable real infrastructure |
| **Transaction** | A payment attempt associated with an order |
| **Trial Metadata** | Data fields describing trial lifecycle and plan state |
| **Webhook** | Asynchronous notification from payment provider about payment status |

---

## 9. Traceability to Roadmap Phases

| Section | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|---------|---------|---------|---------|---------|
| 1. Introduction | ✓ Current/Target State | | | |
| 3.1 Payment Core | Baseline | Testing | | |
| 3.2 Admin Dashboard | Baseline | Testing | | Export |
| 3.3 Landing/Marketing | | | ✓ | Pricing |
| 3.4 Developer Experience | API Versioning | | Docs | SDK |
| 3.5 Commercialization | | | | ✓ |
| 4.x Functional Reqs | Audit | Testing | Deploy | billing-service |
| 5. API Specification | `/api/v1` rollout | | OpenAPI docs | |
| 6.1-6.4 NFRs | Baseline | | Observability | |
| 6.5 Testing | Strategy doc | ✓ CI/CD | | |
| 6.6 Deployment | | | ✓ | |

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-01-01 | FusionXPay Team | Initial requirements |
| 2.0 | 2026-02-06 | FusionXPay Team | Complete rewrite covering Phase 1-4 scope |
| 2.1 | 2026-02-06 | FusionXPay Team | Added Current/Target State, Given/When/Then acceptance criteria, Phase traceability, fixed auth/JWT descriptions to match implementation |
