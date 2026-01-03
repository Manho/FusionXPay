# FusionXPay

FusionXPay is an enterprise-grade, microservices-based payment gateway platform designed to simplify multi-channel payment integrations. It consolidates multiple payment providers (e.g., Stripe, PayPal) into a single RESTful API, ensuring secure, scalable, and resilient payment processing. This repository contains all the source code, configuration, and documentation needed to build, test, and deploy the system.

---

## Table of Contents

- [FusionXPay](#fusionxpay)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Architecture](#architecture)
  - [Project Structure](#project-structure)
  - [Services Overview](#services-overview)
    - [API Gateway](#api-gateway)
    - [Order Service](#order-service)
    - [Payment Service](#payment-service)
    - [Notification Service](#notification-service)
    - [Common Module](#common-module)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Running Locally](#running-locally)
  - [Configuration](#configuration)
  - [Development and Testing](#development-and-testing)
  - [Docker and Deployment](#docker-and-deployment)
  - [CI/CD Pipeline](#cicd-pipeline)
  - [Contributing](#contributing)
  - [License](#license)

---

## Overview

FusionXPay was built to address the challenges enterprises face when managing multiple payment channels. Its core goals include:

- **Multi-Channel Integration:** A single integration point for various payment providers.
- **End-to-End Transaction Management:** From order creation and payment initiation to callback handling and auditing.
- **Security & Compliance:** Incorporating HMAC/token-based validations, HTTPS enforcement, and logging for traceability.
- **Scalability & Resilience:** A microservices architecture with asynchronous communication, ensuring high availability even under heavy loads.

---

## Architecture

FusionXPay leverages modern design principles to ensure a robust and flexible payment platform:

- **Microservices-Based:** Each major function (API Gateway, Order Service, Payment Service, Notification Service) is encapsulated in its own service.
- **Cloud-Native & Containerized:** Built to run in Docker containers, easily orchestrated with Kubernetes.
- **Asynchronous Communication:** Utilizes message queues (Kafka) to decouple services and improve resilience.
- **Observability & Security:** Integrated logging, monitoring, and distributed tracing alongside robust security measures like HTTPS, signature validations, and potential future PCI-DSS compliance.

For an in-depth look at the system’s architecture, please refer to the [Architecture Design Document](./docs/design/architecture.md) and [Process Flow Document](./docs/design/process-flow.md).

---

## Project Structure

```
FusionXPay/
├── docs/                  
│   ├── design/           # Design documents and diagrams
│   │   ├── architecture.md
│   │   ├── process-flow.md
│   │   └── diagrams/     
│   └── requirements/     # Requirements specification
│       └── requirements.md
├── services/             # Microservices implementations
│   ├── api-gateway/      
│   ├── common/           
│   ├── notification-service/
│   ├── order-service/    
│   └── payment-service/  
├── scripts/              # Utility scripts (e.g., run-all.sh)
├── docker-compose.yml    # Docker Compose configuration
├── pom.xml               # Parent Maven project configuration
└── README.md             # This file
```

---

## Services Overview

### API Gateway
- **Role:** Entry point that routes incoming requests to the appropriate microservices.
- **Technologies:** Spring Cloud Gateway, Eureka for service discovery.
- **Key Features:** Rate limiting, authentication, logging, and request metadata correlation.

### Order Service
- **Role:** Manages order lifecycle—from creation to final status update.
- **Endpoints:** 
  - `POST /order/create` – Create a new order.
  - `GET /order/{id}` – Retrieve order details.
- **Technologies:** Spring Boot, JPA, MySQL.

### Payment Service
- **Role:** Processes payments and handles asynchronous callbacks from payment providers.
- **Endpoints:**
  - `POST /payment/request` – Initiate a payment.
  - `POST /payment/callback` – Process payment provider callbacks.
- **Technologies:** Spring Boot, JPA, integration with external payment APIs.

### Notification Service
- **Role:** Sends notifications based on order status changes.
- **Endpoints:** For example, a simple endpoint to test notification delivery.
- **Technologies:** Spring Boot, JPA, Redis, Kafka.

### Common Module
- **Role:** Shared components such as data transfer objects (DTOs) and utility classes.
- **Usage:** Consumed by the microservices for consistent API responses and common logic.

---

## Getting Started

### Prerequisites

- **Java 17**
- **Maven 3.6+**
- **Docker & Docker Compose**
- **MySQL** (or use the provided MySQL Docker container)
- **Redis** (or use the provided Docker container)
- **Kafka & ZooKeeper** (via Docker)

### Running Locally

1. **Clone the repository:**

   ```bash
   git clone https://github.com/yourusername/FusionXPay.git
   cd FusionXPay
   ```

2. **Initialize the database:**

   The `docker-compose.yml` file includes a MySQL service that automatically executes the initialization script located in `./.mysql-init/init.sql`.

3. **Run the services:**

   You can start all services using the provided script:

   ```bash
   chmod +x scripts/run-all.sh
   ./scripts/run-all.sh
   ```

   Alternatively, you can start all containers using Docker Compose:

   ```bash
   docker-compose up --build
   ```

4. **Accessing the APIs:**

   - **API Gateway:** [http://localhost:8080](http://localhost:8080)
   - **Order Service:** [http://localhost:8082/create-order](http://localhost:8082/create-order)
   - **Payment Service:** [http://localhost:8081/create-payment](http://localhost:8081/create-payment)
   - **Notification Service:** [http://localhost:8083/create-notification](http://localhost:8083/create-notification)

---

## Configuration

- **Service Discovery:** Eureka is used for dynamic service registration and discovery.
- **Database Configuration:** Each service has its own configuration in `application.yml` files pointing to the MySQL database.
- **Message Queues:** Kafka is used for asynchronous messaging, decoupling communication between services.
- **Environment Variables:** Copy `.env.example` to `.env` and set provider keys and infrastructure endpoints used by each service.
- **Security & Resilience:** 
  - HTTPS/TLS is enforced.
  - Resilience4j is configured for circuit breaking.
  - HMAC/token-based validation is used for secure callbacks.

Detailed configurations for each service are available in their respective `src/main/resources/application.yml` files.

---

## Development and Testing

- **Building the Project:** Use Maven to build the entire project from the parent directory.

  ```bash
  mvn clean install
  ```

- **Running Tests:** Each microservice includes its own suite of tests. Run tests with:

  ```bash
  mvn test
  ```

---

## Docker and Deployment

FusionXPay is containerized and can be deployed using Docker and Kubernetes. The included `docker-compose.yml` provides a local development environment with services for MySQL, Redis, Kafka, and Eureka.

For production deployments:
- Use Kubernetes with Helm charts for orchestration.
- Leverage rolling updates and auto-scaling to ensure zero downtime.
- Integrate with your CI/CD pipeline for automated builds and deployments.

---

## CI/CD Pipeline

The project is designed for continuous integration and continuous deployment:
- **Source Control:** Git-based branching with pull requests and code reviews.
- **Automated Testing:** Integration with Maven and your preferred CI tool (e.g., GitHub Actions, Jenkins).
- **Containerization:** Each service is built into a Docker container.
- **Deployment:** Automated deployments to staging and production environments using Kubernetes.

---

## Contributing

Contributions are welcome! Please refer to the [CONTRIBUTING.md](./CONTRIBUTING.md) file for guidelines on how to contribute to FusionXPay.

---

## License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.
