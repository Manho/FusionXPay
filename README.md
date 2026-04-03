<p align="center">
  <img src="docs/design/diagrams/logo.svg" alt="FusionXPay" width="96" height="96">
</p>

<h1 align="center"><a href="https://fusionx.fun/">FusionXPay</a></h1>

<p align="center">
  <strong>AI-native Java microservices payment platform — MCP Server, AI CLI, gateway routing, multi-provider payment processing, event-driven notifications, and built-in observability.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot 3.2">
  <img src="https://img.shields.io/badge/Spring%20Cloud-2023-0A8FDC?style=flat-square" alt="Spring Cloud 2023">
  <img src="https://img.shields.io/badge/MCP-Model%20Context%20Protocol-7C3AED?style=flat-square" alt="MCP">
  <img src="https://img.shields.io/badge/Testing-JUnit%20%7C%20Testcontainers%20%7C%20WireMock-6E56CF?style=flat-square" alt="Testing stack">
  <img src="https://img.shields.io/badge/Observability-Prometheus%20%2B%20Grafana%20%2B%20Loki-EA580C?style=flat-square" alt="Observability stack">
</p>

---

## Overview

FusionXPay is an **AI-native** payment platform built with Spring Cloud microservices. Beyond standard payment processing (Stripe and PayPal), webhook-driven status updates, refunds, and async notifications, FusionXPay ships with a full AI integration layer:

- **MCP Server** — Exposes payment and order tools via the Model Context Protocol, allowing AI agents (e.g., Claude, Cursor) to query orders, initiate payments, and trigger refunds with AOP-enforced safety guards.
- **AI CLI** — A picocli-based command-line interface for AI agents and automation pipelines to interact with FusionXPay programmatically.
- **AI Auth** — An OAuth2-inspired browser-based consent flow that lets AI agents acquire scoped tokens for authorized API access.
- **AI Audit** — All AI-originated tool invocations are persisted to Kafka (`ai-audit-log` topic) and consumed by Admin Service for compliance tracking.

---

## Architecture

<p align="center">
  <img src="docs/design/diagrams/architecture.png" alt="FusionXPay Architecture" width="880">
</p>

### Services

| Service | Port | What it does |
|---------|------|--------------|
| API Gateway | 8080 | Routes requests, enforces API key auth, Redis-backed rate limiting |
| Order Service | 8082 | Order lifecycle, merchant-scoped data isolation |
| Payment Service | 8081 | Provider integration (Stripe/PayPal), webhook handling, refunds |
| Notification Service | 8083 | Kafka-driven async notification delivery |
| Admin Service | 8084 | JWT-authenticated admin/merchant management, AI auth sessions, AI audit log consumer |

### AI Layer

| Component | What it does |
|-----------|--------------|
| MCP Server | Exposes FusionXPay tools via Model Context Protocol; AOP safety pipeline guards all invocations |
| AI CLI | picocli-based CLI for AI agents to authenticate, query orders/payments, and trigger actions |
| AI Common | Shared DTOs for AI auth flows (authorize, poll, consent, token exchange, revoke) |

### Infrastructure

| Component | Purpose |
|-----------|---------|
| MySQL | Persistence for all services |
| Redis | Rate limiting (gateway) and caching/idempotency (payment) |
| Kafka | Event bus — `payment-events` (payment→order), `order-events` (order→notification), `ai-audit-log` (AI audit) |
| Eureka | Service discovery |
| Prometheus + Grafana + Loki | Metrics, dashboards, and centralized log aggregation |

---

## AI Integration

### MCP Server

The MCP Server (`ai/ai-mcp-server`) implements the [Model Context Protocol](https://modelcontextprotocol.io/) so that AI agents can interact with FusionXPay as a native tool provider.

**Available tools** (via `FusionXMcpTools`):

| Tool | Description |
|------|--------------|
| `get_order` | Retrieve full order details by order ID or order number |
| `get_order_status` | Get only the status projection for an order |
| `search_orders` | Search merchant orders with optional status, order number, and date filters |
| `query_payment` | Query a payment by transaction ID or order ID |
| `search_payments` | Search merchant payments with optional status and date filters |
| `initiate_payment` | Prepare a payment request — returns `CONFIRMATION_REQUIRED`; must be followed by `confirm_action` |
| `refund_payment` | Prepare a refund request — returns `CONFIRMATION_REQUIRED`; must be followed by `confirm_action` |
| `confirm_action` | Execute a previously prepared write action using its confirmation token |

**AOP Safety Pipeline** — every tool call passes through three aspects in sequence:

```
InputSafetyAspect → ToolAuditAspect → OutputSafetyAspect
```

- `InputSafetyAspect` — validates and sanitizes tool inputs before execution
- `ToolAuditAspect` — emits a Kafka event to `ai-audit-log` for every invocation
- `OutputSafetyAspect` — redacts or prevents sensitive data from leaking to the AI agent

### AI CLI

The AI CLI (`ai/ai-cli`) is a **picocli**-based Spring Boot application that AI agents or automated pipelines can invoke from the command line.

**Available command groups:**

| Command | Description |
|---------|-------------|
| `auth login` | Start the browser-based AI consent flow |
| `auth status` | Check current authentication status |
| `auth logout` | Revoke the current AI token |
| `order get <id>` | Get an order by ID |
| `order status <id>` | Get an order's current status |
| `order search` | Search orders |
| `payment pay` | Initiate a payment |
| `payment confirm` | Confirm a pending payment |
| `payment refund` | Refund a payment |
| `payment query` | Query payment details |
| `payment search` | Search payments |

### AI Auth Flow

AI agents obtain scoped access tokens through a **device-code** consent flow managed by Admin Service (`POST /api/v1/admin/auth/ai/*`). The two-phase poll-then-token exchange is intentional — poll returns only a status, and the token is obtained in a separate `POST /token` call once the status is `APPROVED`.

<p align="center">
  <img src="docs/design/diagrams/ai-auth-flow.png" alt="AI Agent Authorization Flow" width="760">
</p>

> **API base path:** `POST http://localhost:8080/api/v1/admin/auth/ai/{endpoint}` (via API Gateway on port 8080)

### AI Audit Log

Every AI-originated action — whether from the MCP Server or the CLI — is recorded to MySQL via Kafka:

**MCP Server path:** `ToolAuditAspect` (AOP) → publishes `AuditEvent` with `source=MCP` to `ai-audit-log` topic

**CLI path:** `CliExecutionStrategy` (picocli execution hook) → publishes `AuditEvent` with `source=CLI` to `ai-audit-log` topic

Both paths converge at:
1. `AiAuditEventConsumer` in Admin Service consumes from the `ai-audit-log` topic
2. Events are persisted to the `ai_audit_log` table for compliance review
3. Admins can query audit logs via the Admin Service API

---

## Quick Start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 21+ |
| Maven | 3.6+ |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |

### Setup

```bash
git clone https://github.com/Manho/FusionXPay.git
cd FusionXPay
cp .env.always-on.example .env.always-on
```

Edit `.env.always-on` with your infrastructure hosts, database credentials, payment provider keys, and callback URLs.

### Start Everything

```bash
docker compose -f docker-compose.always-on.yml --env-file .env.always-on up -d --build
```

### Verify

```bash
./scripts/check-always-on-health.sh ./.env.always-on
./scripts/verify-service-chain.sh
```

### Useful Endpoints

| Endpoint | Purpose |
|----------|---------|
| `http://localhost:8080/actuator/health` | Gateway health |
| `http://localhost:8080/api/v1/admin/auth/ai/authorize` | AI Agent auth entry point (via gateway) |
| `http://localhost:3001` | Grafana dashboards |
| `http://localhost:9090` | Prometheus |
| `http://localhost:3100` | Loki log query |

---

## Docker Compose Profiles

| File | Use case |
|------|----------|
| `docker-compose.yml` | Local development — infrastructure only (MySQL, Redis, Kafka, Eureka) |
| `docker-compose.prod.yml` | Production image builds |
| `docker-compose.always-on.yml` | Long-running deployment with health checks and resource limits |
| `docker-compose.monitoring.yml` | Observability stack (Prometheus, Grafana, Loki, Promtail) |

---

## Testing

```bash
# Unit tests
mvn test

# Integration tests (Testcontainers spins up MySQL, Redis, Kafka)
mvn verify -pl services/api-gateway

# Service-specific tests
mvn test -pl services/payment-service
mvn test -pl services/admin-service
mvn test -pl ai/ai-mcp-server
mvn test -pl ai/ai-cli

# Runtime verification (requires running stack)
./scripts/check-always-on-health.sh ./.env.always-on
./scripts/verify-service-chain.sh

# End-to-end payment flows (sandbox keys required)
./scripts/e2e-payment-refund.sh stripe
./scripts/e2e-payment-refund.sh paypal
```

---

## Project Structure

```text
FusionXPay/
├── services/
│   ├── api-gateway/           # Spring Cloud Gateway, rate limiting, auth routing
│   ├── order-service/         # Order lifecycle and merchant isolation
│   ├── payment-service/       # Provider integration, webhooks, refunds
│   ├── notification-service/  # Kafka-driven notifications
│   └── admin-service/         # Admin/merchant APIs, AI auth sessions, AI audit log
├── ai/
│   ├── ai-mcp-server/         # Model Context Protocol server with AOP safety pipeline
│   ├── ai-cli/                # Spring Shell CLI for AI agent interactions
│   └── ai-common/             # Shared DTOs for AI auth flows
├── common/                    # Shared DTOs and utilities
├── mysql-init/                # Database initialization scripts
├── scripts/                   # Deploy, verify, backup, rollback, E2E utilities
├── monitoring/                # Prometheus, Grafana, Loki, Promtail configs
├── docs/                      # Architecture, deployment, and testing docs
└── .github/workflows/         # CI, Docker build, and deployment pipelines
```

---

## Documentation

| Document | Purpose |
|----------|---------|
| [Architecture](./docs/design/architecture.md) | System structure and service responsibilities |
| [Process Flow](./docs/design/process-flow.md) | Payment flow diagrams and sequence explanations |
| [Requirements](./docs/requirements/requirements.md) | Product and API requirements |
| [Testing Strategy](./docs/testing/testing-strategy.md) | Test layers and coverage approach |
| [Performance Baseline](./docs/testing/performance-baseline-report.md) | k6 load test results |
| [Reliability Report](./docs/testing/reliability-test-report.md) | Recovery and resilience findings |
| [Operations Runbook](./docs/operations/local-observability-backup.md) | Monitoring, backup, and local ops |
| [Always-On Deployment](./docs/deployment/local-always-on.md) | Long-running deployment setup |
| [Auto Deploy](./docs/deployment/auto-deploy-main.md) | CI/CD deployment automation |

---

## Related

| Project | Description |
|---------|-------------|
| [FusionXPay Frontend](https://github.com/Manho/fusionxpay-web) | Next.js dashboard, landing page, and docs UI |

---

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE) for details.
