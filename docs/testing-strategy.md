# Testing Strategy for FusionXPay (Phase 2 Preparation)

## 1. Testing Philosophy

### 1.1 Test Pyramid
We follow a test pyramid model:
- Unit tests: highest volume, fastest feedback
- Controller/API tests: medium volume, verify HTTP contract
- Integration/E2E tests: lowest volume, verify critical business flows end-to-end

### 1.2 Test Isolation Principles
- Unit tests must isolate business logic from external systems.
- Controller tests should isolate HTTP mapping and validation behavior.
- Integration tests should run with realistic infrastructure and avoid mocking internal services.
- External provider behavior should be mocked with deterministic fixtures.

### 1.3 Coverage Goals
We do not enforce a single global percentage target. Instead, coverage should prioritize critical paths:
- Payment authorization, capture, refund, and idempotency
- Order lifecycle transitions and payment event handling
- Admin authentication and RBAC-sensitive endpoints
- Notification delivery trigger paths

## 2. Backend Testing

### 2.1 Unit Tests
- Scope: service-layer business logic, utility behavior, provider adapters
- Tools: JUnit 5, Mockito
- Typical location pattern: `services/<service>/src/test/java/**/service/*Test.java`
- Strategy: mock external dependencies (`repositories`, `HTTP clients`, message brokers, third-party APIs)

### 2.2 Controller Tests
- Scope: request mapping, response codes, payload validation, auth boundary behavior
- Tools: Spring MockMvc, `@WebMvcTest`
- Typical location pattern: `services/<service>/src/test/java/**/controller/*Test.java`
- Strategy: keep controller tests focused on web layer contracts

### 2.3 Integration Tests
- Scope: critical cross-component flows using real infra semantics
- Tools: `@SpringBootTest`, Testcontainers (MySQL, Kafka)
- Target scenarios:
  - payment creation -> webhook/callback -> order update propagation
  - Kafka event flow between payment/order/notification services
  - transaction and persistence correctness under idempotency rules

### 2.4 External Provider Mocking
- Tools: WireMock (preferred) or MockWebServer
- Scope: Stripe and PayPal external API behavior
- Recommended fixture location: `services/payment-service/src/test/resources/wiremock/`
- Strategy: mock success, decline, timeout, and malformed callback payloads

## 3. Frontend Testing

### 3.1 Component Tests
- Tools: Vitest + React Testing Library
- Scope:
  - form validation and submission states
  - API error rendering
  - auth state handling
  - core UI component behavior

### 3.2 E2E Tests
- Tool: Playwright
- Scope: critical merchant flows
- Initial target scenarios (5):
  1. Login success and redirect to orders
  2. Login failure message behavior
  3. Orders list rendering with API data
  4. Order detail view and navigation back
  5. Logout and session invalidation

## 4. Test Inventory by Service

### 4.1 api-gateway (4)
- `services/api-gateway/src/test/java/com/fusionxpay/api/gateway/ApiGatewayApplicationTest.java` (integration/smoke)
- `services/api-gateway/src/test/java/com/fusionxpay/api/gateway/controller/AuthControllerTest.java` (controller)
- `services/api-gateway/src/test/java/com/fusionxpay/api/gateway/filter/ApiKeyAuthFilterTest.java` (security filter/unit)
- `services/api-gateway/src/test/java/com/fusionxpay/api/gateway/service/UserServiceTest.java` (service/unit)

Gaps:
- No dedicated integration tests for full gateway auth flow with downstream dependencies
- Limited negative-path coverage for API key rotation and malformed headers

### 4.2 payment-service (11)
- `services/payment-service/src/test/java/com/fusionxpay/payment/PaymentApplicationTests.java` (integration/smoke)
- `services/payment-service/src/test/java/com/fusionxpay/payment/controller/PaymentControllerTest.java` (controller)
- `services/payment-service/src/test/java/com/fusionxpay/payment/controller/PaymentControllerUnitTest.java` (controller/unit)
- `services/payment-service/src/test/java/com/fusionxpay/payment/service/PaymentServiceTest.java` (service)
- `services/payment-service/src/test/java/com/fusionxpay/payment/service/PaymentServiceUnitTest.java` (service/unit)
- `services/payment-service/src/test/java/com/fusionxpay/payment/service/PaymentServiceRefundTest.java` (service/refund)
- `services/payment-service/src/test/java/com/fusionxpay/payment/service/IdempotencyServiceTest.java` (service/idempotency)
- `services/payment-service/src/test/java/com/fusionxpay/payment/provider/StripeProviderTest.java` (provider)
- `services/payment-service/src/test/java/com/fusionxpay/payment/provider/PayPalProviderTest.java` (provider)
- `services/payment-service/src/test/java/com/fusionxpay/payment/event/OrderEventProducerTest.java` (event producer)
- `services/payment-service/src/test/java/com/fusionxpay/payment/config/TestConfig.java` (test support)

Gaps:
- No Testcontainers-based integration for real MySQL + Kafka payment flow
- Limited webhook signature verification and replay attack scenarios

### 4.3 order-service (5)
- `services/order-service/src/test/java/com/fusionxpay/order/OrderApplicationTests.java` (integration/smoke)
- `services/order-service/src/test/java/com/fusionxpay/order/controller/OrderControllerTest.java` (controller)
- `services/order-service/src/test/java/com/fusionxpay/order/service/OrderServiceTest.java` (service)
- `services/order-service/src/test/java/com/fusionxpay/order/event/PaymentEventConsumerTest.java` (event consumer)
- `services/order-service/src/test/java/com/fusionxpay/order/config/TestConfig.java` (test support)

Gaps:
- Missing integration test for status transition consistency under duplicate payment events
- Missing failure-path assertions for downstream event or DB failures

### 4.4 notification-service (4)
- `services/notification-service/src/test/java/com/fusionxpay/notification/NotificationApplicationTests.java` (integration/smoke)
- `services/notification-service/src/test/java/com/fusionxpay/notification/controller/NotificationControllerTest.java` (controller)
- `services/notification-service/src/test/java/com/fusionxpay/notification/service/NotificationServiceTest.java` (service)
- `services/notification-service/src/test/java/com/fusionxpay/notification/event/NotificationEventConsumerTest.java` (event consumer)

Gaps:
- No integration test validating end-to-end Kafka event consumption behavior
- Limited retry/failure handling assertions

### 4.5 admin-service (3)
- `services/admin-service/src/test/java/com/fusionxpay/admin/controller/AuthControllerTest.java` (controller)
- `services/admin-service/src/test/java/com/fusionxpay/admin/security/JwtTokenProviderTest.java` (security/unit)
- `services/admin-service/src/test/java/com/fusionxpay/admin/service/AuthServiceTest.java` (service/unit)

Gaps:
- Missing RBAC-focused tests for protected admin endpoints
- Missing integration tests for token lifecycle and refresh/expiry boundaries

### 4.6 common (0)
- No current test files under `common/src/test/java`

Gaps:
- Shared utilities and models should get lightweight unit tests as common logic grows

### 4.7 frontend (0)
- No current test files detected under `fusionxpay-web/src`

Gaps:
- No component tests
- No E2E coverage for merchant critical flows

## 5. CI/CD Integration (Phase 2 Preview)

### 5.1 PR Checks (fast, target < 15 min)
- Backend unit + controller tests
- Frontend lint + type-check + component tests
- Static quality checks (format/lint/security baseline)

### 5.2 Main/Nightly Pipeline (full, target 20-40 min)
- Full backend test suite
- Integration tests using Testcontainers (MySQL, Kafka)
- Frontend E2E suite (Playwright)
- Contract checks for key API routes

### 5.3 Release Gate (strict)
- All required suites green
- No skipped critical-path tests
- No unresolved flaky test issues

## 6. Quality Gates

| Gate | Criteria | Blocking |
|------|----------|----------|
| PR Merge | Unit/controller tests pass, lint clean | Yes |
| Main Build | Full backend + frontend suites pass | Yes |
| Release | Critical path tests pass with no skips | Yes |

## 7. Known Issues and Mitigations

- JDK 23 + Mockito/ByteBuddy compatibility
  - Risk: test instability or runtime instrumentation errors
  - Mitigation: standardize local/CI runtime to JDK 21, or add required `--add-opens` JVM args

- Testcontainers requires Docker-capable CI runners
  - Risk: integration tests cannot execute reliably in constrained environments
  - Mitigation: ensure Docker-in-Docker or equivalent runner capability for integration stages

- Stripe/PayPal sandbox rate limits and nondeterminism
  - Risk: flaky external integration tests
  - Mitigation: use WireMock for most cases, keep minimal sandbox smoke tests only

## 8. Recommended Test Additions (Priority)

1. Payment flow integration tests with Testcontainers (MySQL + Kafka)
2. Frontend component tests with Vitest + React Testing Library
3. Frontend E2E tests with Playwright for 5 critical scenarios
4. Webhook verification and replay-protection tests
5. Admin RBAC authorization tests across protected endpoints
