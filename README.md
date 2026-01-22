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
  <a href="#documentation">Docs</a> •
  <a href="#contributing">Contributing</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk" alt="Java 17">
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

### 🔌 Multi-Provider Integration
- **Stripe** - Cards, wallets, local payments
- **PayPal** - OAuth 2.0, Orders API v2
- Extensible provider architecture

### 💰 Complete Payment Lifecycle
- Payment initiation & processing
- Webhook handling with signature verification
- Full refund support
- Idempotent operations

</td>
<td width="50%">

### 🏗️ Enterprise Architecture
- Microservices with Spring Cloud
- Event-driven with RabbitMQ
- Service discovery with Eureka
- Circuit breaker patterns

### 🔒 Security & Compliance
- HMAC signature validation
- JWT authentication
- Role-based access control
- PCI-DSS ready architecture

</td>
</tr>
</table>

---

## Quick Start

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 17+ |
| Maven | 3.6+ |
| Docker | 20.10+ |
| Docker Compose | 2.0+ |

### 1. Clone & Configure

```bash
git clone https://github.com/Manho/FusionXPay.git
cd FusionXPay

# Copy environment template
cp .env.example .env

# Edit .env with your payment provider credentials
# STRIPE_API_KEY=sk_test_xxx
# PAYPAL_CLIENT_ID=xxx
# PAYPAL_CLIENT_SECRET=xxx
```

### 2. Start Infrastructure

```bash
# Start MySQL, Redis, RabbitMQ via Docker
docker-compose up -d mysql redis rabbitmq
```

### 3. Run Services

```bash
# Option A: Use the startup script
./scripts/run-all.sh

# Option B: Run with Maven
mvn clean install -DskipTests
mvn spring-boot:run -pl services/eureka-server
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

# Test API Gateway
curl http://localhost:8080/actuator/health
```

---

## Architecture

```
                                    ┌──────────────────┐
                                    │   API Gateway    │
                                    │    (Port 8080)   │
                                    └────────┬─────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
                    ▼                        ▼                        ▼
           ┌───────────────┐        ┌───────────────┐        ┌───────────────┐
           │ Order Service │        │Payment Service│        │ Admin Service │
           │  (Port 8082)  │        │  (Port 8081)  │        │  (Port 8084)  │
           └───────┬───────┘        └───────┬───────┘        └───────────────┘
                   │                        │
                   │    ┌───────────────────┤
                   │    │                   │
                   ▼    ▼                   ▼
           ┌───────────────┐        ┌───────────────┐
           │   RabbitMQ    │        │    Redis      │
           │  (Port 5672)  │        │  (Port 6379)  │
           └───────────────┘        └───────────────┘
                   │
                   ▼
           ┌───────────────┐        ┌───────────────┐
           │ Notification  │        │    MySQL      │
           │   Service     │        │  (Port 3306)  │
           │  (Port 8083)  │        └───────────────┘
           └───────────────┘
```

### Service Overview

| Service | Port | Description |
|---------|------|-------------|
| **API Gateway** | 8080 | Request routing, rate limiting, authentication |
| **Eureka Server** | 8761 | Service discovery and registration |
| **Order Service** | 8082 | Order lifecycle management |
| **Payment Service** | 8081 | Payment processing, provider integration |
| **Notification Service** | 8083 | Async notification delivery |
| **Admin Service** | 8084 | Merchant dashboard API |

---

## API Reference

### Create Order

```bash
POST /api/orders
Content-Type: application/json

{
  "amount": 99.99,
  "currency": "USD",
  "description": "Premium Subscription",
  "metadata": {
    "customer_id": "cust_123"
  }
}
```

### Initiate Payment

```bash
POST /api/payment/request
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
POST /api/payment/refund
Content-Type: application/json

{
  "orderId": "ord_abc123",
  "amount": 50.00,
  "reason": "Customer request"
}
```

> 📖 For complete API documentation, see [API Reference](./docs/api/README.md)

---

## Project Structure

```
FusionXPay/
├── services/
│   ├── api-gateway/          # Spring Cloud Gateway
│   ├── eureka-server/        # Service Discovery
│   ├── order-service/        # Order Management
│   ├── payment-service/      # Payment Processing
│   ├── notification-service/ # Notifications
│   └── admin-service/        # Admin Dashboard API
├── common/                   # Shared DTOs & Utils
├── mysql-init/               # Database Initialization
├── scripts/                  # Utility Scripts
├── docs/
│   ├── design/              # Architecture Docs
│   ├── api/                 # API Documentation
│   └── requirements/        # Requirements Spec
└── docker-compose.yml       # Local Development
```

---

## Tech Stack

<table>
<tr>
<td align="center" width="96">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="48" height="48" alt="Java" />
  <br>Java 17
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
</tr>
</table>

**Core Technologies:**
- **Framework:** Spring Boot 3.2, Spring Cloud 2023
- **Database:** MySQL 8.0, Redis 7
- **Messaging:** RabbitMQ
- **Security:** Spring Security, JWT
- **Build:** Maven
- **Container:** Docker, Docker Compose

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture Design](./docs/design/architecture.md) | System architecture and design decisions |
| [Process Flow](./docs/design/process-flow.md) | Payment flow diagrams |
| [Requirements](./docs/requirements/requirements.md) | Functional requirements |
| [API Reference](./docs/api/README.md) | API endpoints documentation |

---

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run specific service tests
mvn test -pl services/payment-service

# Run with coverage report
mvn test jacoco:report
```

### Code Quality

```bash
# Check code style
mvn checkstyle:check

# Run static analysis
mvn spotbugs:check
```

---

## Roadmap

- [x] **Phase 1:** Core Payment Integration (Stripe, PayPal)
- [x] **Phase 2:** Admin Dashboard MVP
- [ ] **Phase 3:** Analytics & Reporting
- [ ] **Phase 4:** Additional Payment Providers (Alipay, WeChat Pay)
- [ ] **Phase 5:** Subscription & Recurring Payments

---

## Contributing

We welcome contributions! Please see our [Contributing Guide](./CONTRIBUTING.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
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
