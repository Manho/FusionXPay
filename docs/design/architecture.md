# FusionXPay System Architecture

## 1. Architectural Overview

### Goal:
Provide a secure, scalable, and modular payment gateway that supports multiple providers (e.g., Stripe, PayPal) through a single API. The architecture should accommodate enterprise-level requirements such as robust logging, transaction auditing, idempotent callbacks, and future PCI-DSS alignment.

### Core Characteristics:
1. **Microservices-Based**: Each major function (Order Management, Payment Handling, Notifications) is encapsulated in its own service for independent deployment and scaling.
2. **Cloud-Native & Containerized**: Services run in Docker containers and can be orchestrated by Kubernetes.
3. **Message-Driven Architecture**: A message queue (Kafka) serves as the backbone for inter-service communication, enabling reliable status updates, notifications, and event-driven processing.
4. **Security & Compliance Focus**: HMAC signatures or tokens for callback validation, enforced HTTPS, secure secrets management, and logging for compliance.
5. **Observability**: Monitoring, logging, and tracing integrated from the outset to ensure quick root-cause analysis and performance tuning.

## 2. High-Level Architecture Diagram

Below is a conceptual diagram incorporating enterprise requirements such as end-to-end transaction management, robust security checks, and centralized logging.

![Architecture Diagram](./diagrams/architecture.png)


### Components:
- **API Gateway** (port 8080): Handles request routing, JWT bearer authentication, Redis-backed rate limiting, service discovery via Eureka, and emits `platform-audit-log` ingress events.
- **Order Service** (port 8082): Manages order lifecycle and merchant-scoped data isolation; consumes `payment-events` from Kafka to update order status.
- **Payment Service** (port 8081): Processes payment requests via Stripe/PayPal, handles webhooks, manages refunds, and publishes to the `payment-events` Kafka topic.
- **Notification Service** (port 8083): Consumes the `order-events` Kafka topic and sends async notifications to merchants on payment success or failure.
- **Admin Service** (port 8084): JWT-authenticated admin/merchant management and AI auth session flow.
- **MCP Server**: Exposes FusionXPay as a Model Context Protocol (MCP) tool provider. Tool invocations pass through an AOP pipeline (`InputSafetyAspect → ToolAuditAspect → OutputSafetyAspect`), where `ToolAuditAspect` contributes `X-Audit-*` metadata rather than publishing Kafka events directly.
- **AI CLI**: picocli-based Spring Boot CLI for AI agents; `CliExecutionStrategy` contributes `X-Audit-*` metadata to every gateway-bound request.
- **Apache Kafka**: Event bus with three active topics — `payment-events`, `order-events`, and `platform-audit-log`.
- **MySQL**: Shared persistent storage for all services.
- **Redis**: Rate limiting (gateway) and idempotency/caching (payment service).
- **Monitoring & Logging**: Prometheus + Grafana + Loki + Promtail for metrics, dashboards, and centralized log aggregation.

## 3. Microservices Breakdown

### 3.1 API Gateway
- **Tech**: Spring Cloud Gateway
- **Responsibilities**:
  - Routes external requests (e.g., `/api/orders`, `/api/payment/request`, `/api/payment/callback`) to internal services.
  - Implements rate-limiting, authentication, and IP whitelisting.
  - Logs ingress request metadata for platform audit (request time, source, merchant ID, correlation ID).

### 3.2 Order Service
- **Endpoints**:
  - `POST /api/orders` – Creates a new order, sets status to `NEW`.
  - `GET /api/orders/{orderId}` – Retrieves order status and details.
- **Functionality**:
  - Manages order lifecycle transitions (NEW → PROCESSING → SUCCESS/FAILED).
  - Stores order data in MySQL/PostgreSQL with ACID transactions.
  - Subscribes to order status update messages from Payment Service.
  - Updates order status based on received messages.

### 3.3 Payment Service
- **Endpoints**:
  - `POST /api/payment/request` – Initiates payment for a specific order.
  - `POST /api/payment/callback` – Processes asynchronous notifications from payment providers.
- **Functionality**:
  - Integrates with external payment APIs using Strategy/Adapter pattern.
  - Ensures callback authenticity (HMAC, tokens) and idempotency.
  - Publishes messages:
    - `ORDER_STATUS_UPDATE` for Order Service to update order status to PROCESSING.
    - `ORDER_STATUS_FINALIZE` for Order Service to update to SUCCESS/FAILED.
    - `PAYMENT_RESULT` for Notification Service to send merchant notifications.

### 3.4 Notification Service
- **Trigger**: Subscribes to `PAYMENT_RESULT` messages.
- **Functionality**:
  - Sends notifications to merchants about payment outcomes.
  - Logs notification delivery for traceability.

### 3.5 Message Queue System
- **Tech**: Apache Kafka
- **Topics**:
  - `payment-events` — Payment Service publishes status updates; Order Service consumes to update order status
  - `order-events` — Order Service publishes final payment outcomes; Notification Service consumes to send merchant notifications
  - `platform-audit-log` — API Gateway publishes one ingress audit event for every external request; MCP/CLI enrich requests with `X-Audit-*` metadata so source and action names are preserved downstream
- **Properties**:
  - Persistent message storage
  - Delivery guarantees
  - Dead letter queues for failed message processing

## 4. Security & Compliance Measures
- **Mandatory HTTPS/TLS**: Enforces secure communication.
- **HMAC or Token-Based Validation**: Protects against tampering and replay attacks.
- **PCI-DSS Considerations**: Architecture supports future compliance with tokenization options.
- **Message Security**: Secure access to message queue, potentially encrypted payloads for sensitive data.

## 5. Deployment & DevOps Pipeline

### 5.1 CI/CD Workflow
1. **Source Control**: Git-based branching, PRs, code reviews.
2. **Build & Test**: Maven/Gradle + GitHub Actions/Jenkins.
3. **Containerization**: Each service runs in Docker.
4. **Deployment**:
   - Staging: Docker Compose / Minikube.
   - Production: Kubernetes with Helm.
   - Rolling updates for zero downtime.

### 5.2 Environments
- **Local**: Docker Compose for development.
- **Staging**: Kubernetes cluster for integration testing.
- **Production**: Auto-scaled Kubernetes with redundancy.

## 6. Data Model (Simplified)

### Order Table
| Column       | Type       | Description             |
|-------------|-----------|-------------------------|
| orderId     | UUID      | Unique identifier       |
| amount      | Decimal   | Order amount            |
| currency    | String    | Currency code           |
| status      | Enum      | NEW, PROCESSING, SUCCESS, FAILED |
| createdAt   | Timestamp | Order creation time     |
| updatedAt   | Timestamp | Last status update time |

### PaymentTransaction Table
| Column       | Type       | Description             |
|-------------|-----------|-------------------------|
| transactionId | UUID      | Payment transaction ID |
| orderId     | UUID      | Associated order ID    |
| provider    | String    | Payment provider name  |
| status      | Enum      | INITIATED, SUCCESS, FAILED |
| providerRef | String    | Provider reference ID  |
| createdAt   | Timestamp | Transaction creation time |
| updatedAt   | Timestamp | Last status update time |

### Notification Table (Optional)
| Column       | Type       | Description             |
|-------------|-----------|-------------------------|
| notificationId | UUID    | Notification ID        |
| orderId     | UUID      | Associated order ID    |
| type        | String    | Notification type      |
| status      | Enum      | SENT, FAILED, PENDING  |
| createdAt   | Timestamp | Creation time          |

## 7. Concurrency & Reliability Enhancements
- **Message Queue**: Decouples processing logic and enables reliable communication.
- **Idempotent Callbacks**: Prevents duplicate status updates.
- **Distributed Locking (Redis)**: Avoids race conditions.
- **Circuit Breakers (Resilience4j)**: Handles external downtime.
- **Retries & DLQs (Dead Letter Queues)**: Ensures reliable event processing.
- **Message Deduplication**: Prevents duplicate message processing.

## 8. Final Recommendations
- Enhance **security** with tokenization & pen testing.
- Expand **payment providers** via Strategy pattern.
- Implement **real-time monitoring** dashboards.
- Plan for **geo-distributed deployment** if needed.
- Consider **exactly-once delivery semantics** for critical message flows.
- Implement comprehensive **message replay capabilities** for recovery scenarios.

## Conclusion
FusionXPay's message-driven microservices architecture ensures scalability, security, and reliability while allowing for future expansion. The decoupled services communicating via message queues provide flexibility for component updates and failure isolation. This approach balances compliance needs with flexibility, positioning the platform for enterprise-grade payment processing.
