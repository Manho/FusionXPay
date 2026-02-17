<p align="center">
  <img src="docs/design/diagrams/logo.svg" alt="FusionXPay Logo" width="120" height="120">
</p>

<h1 align="center">FusionXPay</h1>

<p align="center">
  <strong>Enterprise-Grade Microservices Payment Gateway Platform</strong>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#api-reference">API</a> •
  <a href="#observability">Observability</a> •
  <a href="#documentation">Docs</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot 3.2">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="MIT License">
  <img src="https://img.shields.io/badge/PRs-welcome-brightgreen?style=flat-square" alt="PRs Welcome">
</p>

---

## Overview

**FusionXPay** is a production-ready payment gateway platform that unifies multiple payment providers (Stripe, PayPal, and more) into a single, elegant RESTful API. Built with enterprise requirements in mind, it provides secure, scalable, and resilient payment processing for businesses of all sizes.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Your Application                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     FusionXPay Gateway                           │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────────────┐ │
│  │ Stripe  │  │ PayPal  │  │ Alipay  │  │  More Providers...  │ │
│  └─────────┘  └─────────┘  └─────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Features

<table>
<tr>
<td width="50%">

### Multi-Provider Integration
- **Stripe** — Checkout sessions, webhook-driven status
- **PayPal** — OAuth 2.0, Orders API v2, capture + webhooks
- Extensible provider architecture

### Complete Payment Lifecycle
- Payment initiation & checkout redirect
- Webhook handling with signature verification
- Full refund support (Stripe + PayPal)
- Idempotent capture & status updates

</td>
<td width="50%">

### Enterprise Architecture
- Microservices with Spring Cloud
- Event-driven with Apache Kafka
- Service discovery with Eureka
- Redis token-bucket rate limiting

### Security & Compliance
- Stripe webhook signature validation
- PayPal webhook signature verification
- JWT authentication for admin dashboard
- API key authentication with merchant isolation
- Role-based access control (RBAC)

</td>
</tr>
</table>

---

## Quick Start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 21+ |
| Maven | 3.6+ |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |

### 1. Clone & Configure

```bash
git clone https://github.com/Manho/FusionXPay.git
cd FusionXPay

# Copy environment template and fill in your provider credentials
cp .env.always-on.example .env
```

### 2. Start with Docker Compose

```bash
# Start all services (infrastructure + application)
docker compose -f docker-compose.always-on.yml --env-file .env up -d

# Verify all services are healthy
./scripts/check-always-on-health.sh
```

### 3. Run Locally (Development)

```bash
# Start infrastructure only
docker compose up -d

# Build all services
mvn clean install -DskipTests

# Run services individually
mvn spring-boot:run -pl services/api-gateway
mvn spring-boot:run -pl services/order-service
mvn spring-boot:run -pl services/payment-service
mvn spring-boot:run -pl services/notification-service
mvn spring-boot:run -pl services/admin-service
```

### 4. Verify Installation

```bash
# Check Eureka Dashboard
open http://localhost:8761

# Test API Gateway health
curl http://localhost:8080/actuator/health

# Run service chain verification
./scripts/verify-service-chain.sh
```

---

## Architecture

<p align="center">
  <img src="docs/design/diagrams/architecture.svg" alt="FusionXPay Architecture" width="880">
</p>

### Service Overview

| Service | Port | Description |
|---------|------|-------------|
| **API Gateway** | 8080 | Request routing, Redis rate limiting, API key authentication |
| **Eureka Server** | 8761 | Service discovery and registration |
| **Order Service** | 8082 | Order lifecycle management, merchant isolation |
| **Payment Service** | 8081 | Payment processing, provider integration, webhook handling |
| **Notification Service** | 8083 | Async notification delivery via Kafka |
| **Admin Service** | 8084 | Merchant dashboard API, JWT authentication |

---

## API Reference

All API endpoints are versioned under `/api/v1`.

### Create Order

```bash
POST /api/v1/orders
X-API-Key: your-api-key
Content-Type: application/json

{
  "amount": 99.99,
  "currency": "USD",
  "description": "Premium Subscription"
}
```

### Initiate Payment

```bash
POST /api/v1/payment/request
X-API-Key: your-api-key
Content-Type: application/json

{
  "orderId": "ord_abc123",
  "provider": "stripe",
  "returnUrl": "https://yoursite.com/success",
  "cancelUrl": "https://yoursite.com/cancel"
}
```

### Process Refund

```bash
POST /api/v1/payment/refund
X-API-Key: your-api-key
Content-Type: application/json

{
  "orderId": "ord_abc123",
  "amount": 50.00,
  "reason": "Customer request"
}
```

> For complete API documentation, see [API Reference](./docs/api/README.md)

---

## Testing

### Unit & Integration Tests

```bash
# Run all unit tests
mvn test

# Run specific service tests
mvn test -pl services/payment-service

# Run integration tests (requires Docker)
mvn verify -pl services/api-gateway
```

Integration tests use [Testcontainers](https://testcontainers.com/) with MySQL, Redis, and Kafka containers, plus [WireMock](https://wiremock.org/) for provider API simulation.

### E2E Payment Flow

```bash
# Full payment + refund E2E test (requires running services + provider sandbox keys)
./scripts/e2e-payment-refund.sh stripe
./scripts/e2e-payment-refund.sh paypal
```

### Performance Tests

```bash
# Run k6 performance baseline (requires k6 installed)
k6 run tests/performance/login.js
k6 run tests/performance/order-list.js
k6 run tests/performance/payment-request.js
```

### Service Chain Verification

```bash
# Verify cross-service communication (Kafka, Eureka, API routing)
./scripts/verify-service-chain.sh
```

---

## Observability

FusionXPay includes a built-in observability stack:

| Component | Port | Purpose |
|-----------|------|---------|
| **Prometheus** | 9090 | Metrics collection |
| **Grafana** | 3000 | Dashboards & alerting |
| **Loki** | 3100 | Log aggregation |
| **Promtail** | — | Log shipping |

### Pre-built Grafana Dashboards

- **Overview** — Service health, request rates, error rates
- **JVM** — Heap usage, GC metrics, thread pools
- **HTTP API** — Latency percentiles, throughput, status codes
- **Infrastructure** — Docker resource usage
- **Logs** — Centralized log search via Loki

```bash
# Start monitoring stack
docker compose -f docker-compose.monitoring.yml up -d

# Access Grafana
open http://localhost:3000  # admin / admin
```

---

## Deployment

### Docker Production Deployment

```bash
# Build all service images
docker compose -f docker-compose.prod.yml build

# Deploy with always-on profile (includes resource limits and health checks)
docker compose -f docker-compose.always-on.yml --env-file .env up -d
```

### CI/CD

GitHub Actions workflows are included:

| Workflow | Trigger | Description |
|----------|---------|-------------|
| `backend-ci.yml` | PR | Fast unit tests |
| `backend-ci-full.yml` | Main / Nightly | Full unit + integration tests |
| `docker-build.yml` | Release tag | Build & push images to GHCR |
| `deploy-local-main.yml` | Main merge | Auto-deploy to self-hosted runner with rollback |

---

## Project Structure

```
FusionXPay/
├── services/
│   ├── api-gateway/          # Spring Cloud Gateway + Rate Limiting
│   ├── eureka-server/        # Service Discovery
│   ├── order-service/        # Order Management + Merchant Isolation
│   ├── payment-service/      # Payment Processing + Webhook Handling
│   ├── notification-service/ # Kafka-driven Notifications
│   └── admin-service/        # Admin Dashboard API + JWT Auth
├── common/                   # Shared DTOs & Utils
├── mysql-init/               # Database Initialization
├── scripts/                  # Deploy, Verify, E2E, Backup Scripts
├── tests/
│   └── performance/          # k6 Performance Test Scripts
├── monitoring/               # Prometheus, Grafana, Loki Config
├── docs/
│   ├── design/              # Architecture Docs
│   ├── api/                 # API Documentation
│   ├── testing/             # Test Reports & Strategy
│   ├── operations/          # Runbooks & Checklists
│   └── requirements/        # Product Requirements
├── .github/workflows/       # CI/CD Pipelines
├── docker-compose.yml           # Local development
├── docker-compose.prod.yml      # Production build
├── docker-compose.always-on.yml # Always-on deployment
└── docker-compose.monitoring.yml # Observability stack
```

---

## Tech Stack

<table>
<tr>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="48" height="48" alt="Java" />
  <br>Java 21
</td>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="48" height="48" alt="Spring" />
  <br>Spring Boot
</td>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/mysql/mysql-original.svg" width="48" height="48" alt="MySQL" />
  <br>MySQL
</td>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/redis/redis-original.svg" width="48" height="48" alt="Redis" />
  <br>Redis
</td>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="48" height="48" alt="Docker" />
  <br>Docker
</td>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/apachekafka/apachekafka-original.svg" width="48" height="48" alt="Kafka" />
  <br>Kafka
</td>
</tr>
</table>

**Core:**
- **Framework:** Spring Boot 3.2, Spring Cloud 2023
- **Database:** MySQL 8.0, Redis 7
- **Messaging:** Apache Kafka
- **Security:** Spring Security, JWT, API Key Authentication
- **Rate Limiting:** Spring Cloud Gateway + Redis Token Bucket
- **Build:** Maven, Docker, GitHub Actions

**Observability:**
- **Metrics:** Prometheus, Micrometer
- **Dashboards:** Grafana (5 pre-built dashboards)
- **Logging:** Loki + Promtail
- **Alerting:** Prometheus alert rules

**Testing:**
- **Unit:** JUnit 5, Mockito
- **Integration:** Testcontainers, WireMock
- **Performance:** k6
- **E2E:** Shell-based payment flow scripts

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture Design](./docs/design/architecture.md) | System architecture and design decisions |
| [Process Flow](./docs/design/process-flow.md) | Payment flow diagrams |
| [Requirements](./docs/requirements/requirements.md) | Product requirements specification |
| [API Reference](./docs/api/README.md) | API endpoints documentation |
| [Testing Strategy](./docs/testing/testing-strategy.md) | Test layering and coverage approach |
| [Performance Report](./docs/testing/performance-baseline-report.md) | k6 performance baseline results |
| [Reliability Report](./docs/testing/reliability-test-report.md) | Service recovery and resilience tests |
| [Operations Runbook](./docs/operations/local-observability-backup.md) | Deployment and monitoring guide |

---

## Contributing

We welcome contributions! Please see our [Contributing Guide](./CONTRIBUTING.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'feat: add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE) file for details.

---

<p align="center">
  <a href="https://github.com/Manho/FusionXPay/issues">Report Bug</a> •
  <a href="https://github.com/Manho/FusionXPay/issues">Request Feature</a>
</p>
