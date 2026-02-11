# Security Baseline Report

> **Project**: FusionXPay
> **Date**: 2026-02-11
> **Phase**: 3.5 — Security Hardening
> **Method**: Static code analysis + architecture review
> **Environment**: Middleware host `192.168.50.225` (offline at time of review; runtime tests pending)

---

## Executive Summary

| Category | Tests | PASS | FAIL | WARN | Notes |
|----------|-------|------|------|------|-------|
| Authentication Bypass | 6 | 5 | 0 | 1 | Actuator endpoints exposed |
| SQL Injection | 3 | 3 | 0 | 0 | JPA parameterized queries |
| XSS / Output Encoding | 2 | 2 | 0 | 0 | JSON API, no HTML rendering |
| Information Leakage | 4 | 2 | 1 | 1 | admin-service leaks exception messages |
| Dependency Vulnerabilities | 1 | - | - | - | OWASP plugin added; scan pending |
| Configuration Security | 5 | 3 | 0 | 2 | Actuator + API Key storage |
| **Total** | **21** | **15** | **1** | **4** | |

**Overall Assessment**: The application demonstrates solid security fundamentals with JWT/API Key authentication, parameterized queries, and proper error handling in most services. Key areas for improvement include restricting actuator endpoint access and sanitizing admin-service error responses.

---

## 1. Authentication Bypass Tests

### 1.1 No Token — Protected Endpoint

| Item | Detail |
|------|--------|
| **Target** | `GET /api/v1/orders` (order-service via api-gateway) |
| **Test** | Send request without `X-API-Key` header |
| **Expected** | 401 Unauthorized |
| **Code Evidence** | `ApiKeyAuthFilter.java:44` — checks `X-API-Key` header, returns 401 if missing |
| **Result** | **PASS** |

```java
// ApiKeyAuthFilter.java — relevant logic
String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
if (apiKey == null || apiKey.isEmpty()) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    // returns 401
}
```

### 1.2 Expired JWT Token

| Item | Detail |
|------|--------|
| **Target** | `GET /api/v1/admin/orders` (admin-service) |
| **Test** | Send request with expired JWT |
| **Expected** | 401 Unauthorized |
| **Code Evidence** | `JwtTokenProvider.java:84` — catches `ExpiredJwtException`, returns `false` from `validateToken()` |
| **Result** | **PASS** |

```java
// JwtTokenProvider.java:78-94
public boolean validateToken(String token) {
    try {
        parseClaims(token);
        return true;
    } catch (ExpiredJwtException ex) {
        log.error("Expired JWT token");
    }
    // ... other exception handlers
    return false;
}
```

### 1.3 Tampered JWT Token (Modified Payload)

| Item | Detail |
|------|--------|
| **Target** | `GET /api/v1/admin/orders` (admin-service) |
| **Test** | Modify JWT payload (change `role` claim), keep original signature |
| **Expected** | 401 Unauthorized |
| **Code Evidence** | `JwtTokenProvider.java:103-108` — uses `verifyWith(secretKey)` which validates HMAC signature |
| **Result** | **PASS** |

The HMAC-SHA signature verification ensures that any payload modification invalidates the token. The `MalformedJwtException` or `JwtException` handler catches this case.

### 1.4 Invalid API Key

| Item | Detail |
|------|--------|
| **Target** | `POST /api/v1/payment/request` (payment-service via api-gateway) |
| **Test** | Send request with `X-API-Key: invalid-key-12345` |
| **Expected** | 401 Unauthorized |
| **Code Evidence** | `ApiKeyAuthFilter.java` — calls `userService.getUserByApiKey(apiKey)`, returns 401 if not found |
| **Result** | **PASS** |

### 1.5 JWT with Wrong Signing Key

| Item | Detail |
|------|--------|
| **Target** | `GET /api/v1/admin/orders` (admin-service) |
| **Test** | Generate JWT signed with different HMAC secret |
| **Expected** | 401 Unauthorized |
| **Code Evidence** | `JwtTokenProvider.java:90` — catches `JwtException` for signature mismatch |
| **Result** | **PASS** |

### 1.6 Actuator Endpoints — Unauthenticated Access

| Item | Detail |
|------|--------|
| **Target** | `GET /actuator/health`, `GET /actuator/env`, `GET /actuator/beans` |
| **Test** | Access actuator endpoints without any authentication |
| **Expected** | Should require authentication for sensitive endpoints |
| **Code Evidence** | `SecurityConfig.java:50` — `.requestMatchers("/actuator/**").permitAll()` |
| **Result** | **WARN** — Actuator endpoints are publicly accessible |

**Risk**: The `/actuator/env` endpoint can expose environment variables including secrets. The `/actuator/beans` endpoint reveals application internals.

**Recommendation**: Restrict actuator access to health endpoint only:
```java
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

---

## 2. Injection Tests

### 2.1 SQL Injection — Order Number Filter

| Item | Detail |
|------|--------|
| **Target** | `GET /api/v1/orders?orderNumber=' OR 1=1 --` |
| **Code Evidence** | `OrderRepository.java` — uses JPA `@Query` with `@Param` annotations |
| **Result** | **PASS** |

```java
// OrderRepository.java — parameterized query
@Query("SELECT o FROM Order o WHERE " +
       "(:status IS NULL OR o.status = :status) AND " +
       "(:userId IS NULL OR o.userId = :userId) AND " +
       "(:orderNumber IS NULL OR o.orderNumber LIKE %:orderNumber%)")
Page<Order> findWithFilters(
    @Param("status") OrderStatus status,
    @Param("userId") Long userId,
    @Param("orderNumber") String orderNumber,
    Pageable pageable);
```

JPA/Hibernate parameterized queries prevent SQL injection by treating parameters as values, not SQL fragments.

### 2.2 SQL Injection — Login Credentials

| Item | Detail |
|------|--------|
| **Target** | `POST /api/v1/admin/auth/login` with `email: "admin' OR 1=1 --"` |
| **Code Evidence** | Spring Data JPA `findByEmail()` uses parameterized query internally |
| **Result** | **PASS** |

### 2.3 XSS — API Response Encoding

| Item | Detail |
|------|--------|
| **Target** | All API endpoints returning user-generated content |
| **Analysis** | API returns JSON responses only (Content-Type: application/json). No HTML rendering on backend. |
| **Result** | **PASS** — JSON APIs are not vulnerable to reflected XSS. Frontend (React/Next.js) handles output encoding. |

---

## 3. Information Leakage

### 3.1 Error Response — Order Service

| Item | Detail |
|------|--------|
| **Target** | Trigger 500 error in order-service |
| **Code Evidence** | `GlobalExceptionHandler.java:66-74` |
| **Result** | **PASS** |

```java
// order-service GlobalExceptionHandler — generic 500
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    ErrorResponse errorResponse = ErrorResponse.builder()
            .message("An unexpected error occurred")  // Generic message, no stack trace
            .build();
    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
}
```

### 3.2 Error Response — Payment Service

| Item | Detail |
|------|--------|
| **Target** | Trigger 500 error in payment-service |
| **Code Evidence** | `GlobalExceptionHandler.java:56-68` |
| **Result** | **PASS** |

```java
// payment-service GlobalExceptionHandler — generic 500
.message("An unexpected error occurred. Please try again later.")  // Generic, safe
```

### 3.3 Error Response — Admin Service

| Item | Detail |
|------|--------|
| **Target** | Trigger 500 error in admin-service |
| **Code Evidence** | `GlobalExceptionHandler.java:58-65` |
| **Result** | **FAIL** — Exception message exposed to client |

```java
// admin-service GlobalExceptionHandler — LEAKS exception message
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
    response.put("message", ex.getMessage());  // ⚠️ Leaks internal error details
    response.put("code", "INTERNAL_ERROR");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
}
```

**Risk**: `ex.getMessage()` can contain internal details such as database connection strings, class names, or stack context. An attacker could use this information for further attacks.

**Recommendation**: Replace with generic message:
```java
response.put("message", "An internal error occurred");
```

### 3.4 HTTP Response Headers

| Item | Detail |
|------|--------|
| **Target** | Check `Server`, `X-Powered-By` response headers |
| **Analysis** | Spring Boot embedded Tomcat/Netty default behavior |
| **Result** | **WARN** — No explicit header suppression configured |

**Recommendation**: Add to `application.yml`:
```yaml
server:
  server-header: ""
```

And add security headers via filter or `spring.security.headers`:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000`

---

## 4. Dependency Vulnerability Scan

### 4.1 Backend — OWASP Dependency Check

| Item | Detail |
|------|--------|
| **Tool** | OWASP dependency-check-maven plugin (v10.0.4) |
| **Status** | Plugin added to root `pom.xml` |
| **Command** | `mvn dependency-check:aggregate -DskipTests` |
| **Result** | **PENDING** — Requires network access to download NVD database |

The OWASP Dependency Check plugin has been added to the project. Run the following command when the environment is available:

```bash
mvn dependency-check:aggregate -DskipTests
# Report generated at: target/dependency-check-report.html
```

### 4.2 Frontend — npm audit

| Item | Detail |
|------|--------|
| **Tool** | `npm audit` |
| **Command** | `cd /Users/manho/src/fusionxpay-web && npm audit` |
| **Result** | **9 vulnerabilities** (7 moderate, 2 high) |

**High Severity:**
| Package | Vulnerability | Advisory |
|---------|--------------|----------|
| `next` 15.6.0-canary.0 - 16.1.4 | DoS via Image Optimizer `remotePatterns` config | GHSA-9g9p-9gw9-jx7f |
| `next` 15.6.0-canary.0 - 16.1.4 | Unbounded Memory Consumption via PPR Resume Endpoint | GHSA-5f7q-jpqc-wp7h |

**Moderate Severity:**
| Package | Issue |
|---------|-------|
| `vite` (dev dependency) | 5 vulnerabilities across vite ecosystem |
| `vitest` (dev dependency) | Transitive via vite |

**Assessment**: The `next` high-severity issues are self-hosted DoS vulnerabilities. Since FusionXPay frontend is deployed on **Vercel** (managed hosting), these are **mitigated by the hosting platform**. The `vite`/`vitest` vulnerabilities are dev-only dependencies and do not affect production.

**Recommendation**: Update `next` to `>=16.1.6` when compatible. Run `npm audit fix` for dev dependency updates.

---

## 5. Configuration Security

### 5.1 CSRF Protection

| Item | Detail |
|------|--------|
| **Status** | Disabled across all services |
| **Assessment** | **PASS** — Appropriate for stateless API with JWT/API Key auth. No session cookies used. |

### 5.2 Session Management

| Item | Detail |
|------|--------|
| **Status** | `SessionCreationPolicy.STATELESS` (admin-service) |
| **Assessment** | **PASS** — No session fixation risk |

### 5.3 Password Storage

| Item | Detail |
|------|--------|
| **Status** | BCrypt via `BCryptPasswordEncoder` (default strength factor 10) |
| **Assessment** | **PASS** — Industry standard |

### 5.4 API Key Storage

| Item | Detail |
|------|--------|
| **Status** | Plaintext UUID stored in database |
| **Assessment** | **WARN** — API keys should be hashed (store hash, compare on request) |

**Risk**: Database compromise exposes all API keys in plaintext.

**Recommendation** (future phase): Hash API keys with BCrypt or SHA-256, store only the hash. Show the full key to the user only at creation time.

### 5.5 CORS Configuration

| Item | Detail |
|------|--------|
| **Status** | Hardcoded to `localhost:3000` and `localhost:3001` |
| **Assessment** | **PASS** (restrictive) — Being improved to env-var-based in Task 1 (AI-1) |

---

## 6. Architecture Security Notes

### 6.1 API Gateway Auth Model

The API Gateway `SecurityConfig` uses `permitAll()` for all exchanges, delegating authentication entirely to `ApiKeyAuthFilter`. This is an intentional design choice:

```java
// api-gateway SecurityConfig.java
.authorizeExchange(exchange -> exchange.anyExchange().permitAll())
```

The `ApiKeyAuthFilter` runs with priority `-100` (highest) and enforces authentication for all routes except:
- `/api/v1/auth/*` (registration)
- `/swagger-ui/*`, `/v3/api-docs`, `/swagger-resources`, `/webjars/*`

**Assessment**: Acceptable for current architecture, but consider migrating auth enforcement to Spring Security filter chain for better audit trail and standard compliance.

### 6.2 JWT Configuration

| Parameter | Value | Assessment |
|-----------|-------|------------|
| Algorithm | HMAC-SHA (symmetric) | Acceptable for single-service JWT |
| Expiration | 24 hours (86400000ms) | Consider reducing to 1-4 hours for production |
| Secret source | `JWT_SECRET` env var | Good — not hardcoded |
| Claims | `merchantId`, `role`, `email` | Minimal, appropriate |

---

## 7. Runtime Test Commands

The following `curl` commands can be used to validate security controls when the environment is online:

```bash
# Set base URL
BASE_URL="http://192.168.50.225:8080"

# --- 1. Authentication Bypass Tests ---

# 1.1 No API Key → expect 401
curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/orders"
# Expected: 401

# 1.2 Invalid API Key → expect 401
curl -s -o /dev/null -w "%{http_code}" \
  -H "X-API-Key: invalid-key-12345" \
  "$BASE_URL/api/v1/orders"
# Expected: 401

# 1.3 No JWT → admin endpoint → expect 401
curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/admin/orders"
# Expected: 401

# 1.4 Expired JWT → expect 401
# (generate with past expiration date using jwt.io or similar)

# 1.5 Tampered JWT → expect 401
# (modify payload base64, keep original signature)

# --- 2. SQL Injection Tests ---

# 2.1 Order number parameter injection
curl -s -o /dev/null -w "%{http_code}" \
  -H "X-API-Key: $API_KEY" \
  "$BASE_URL/api/v1/orders?orderNumber=%27%20OR%201%3D1%20--"
# Expected: 200 (but returns 0 matching results, not all records)

# 2.2 Login email injection
curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/v1/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin'\'' OR 1=1 --","password":"test"}'
# Expected: 401

# --- 3. Information Leakage Tests ---

# 3.1 Check Server header
curl -sI "$BASE_URL/api/v1/orders" | grep -i "server\|x-powered-by"
# Expected: No version info exposed

# 3.2 Actuator endpoint access
curl -s "$BASE_URL/actuator/health" | head -20
# Expected: Should return health status (currently open — see WARN finding)

# 3.3 Actuator env (sensitive)
curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/env"
# Expected: Should return 401/403 (currently may return 200 — see WARN finding)
```

---

## 8. Summary of Findings

### Must Fix (Before Production)

| # | Finding | Service | Severity | Recommendation |
|---|---------|---------|----------|----------------|
| F1 | `RuntimeException` handler leaks `ex.getMessage()` | admin-service | **HIGH** | Replace with generic error message |

### Should Fix (Recommended)

| # | Finding | Service | Severity | Recommendation |
|---|---------|---------|----------|----------------|
| F2 | Actuator endpoints publicly accessible | admin-service | **MEDIUM** | Restrict `/actuator/**` to health only |
| F3 | No security response headers | all services | **MEDIUM** | Add X-Content-Type-Options, X-Frame-Options, HSTS |
| F4 | API Keys stored in plaintext | api-gateway DB | **MEDIUM** | Hash API keys in database (future phase) |
| F5 | JWT expiration too long (24h) | admin-service | **LOW** | Reduce to 1-4 hours for production |

### Positive Findings

| # | Finding | Service | Assessment |
|---|---------|---------|------------|
| P1 | Parameterized JPA queries prevent SQL injection | order-service | Excellent |
| P2 | JWT validation handles all exception types | admin-service | Excellent |
| P3 | API Key auth enforced at gateway level | api-gateway | Good |
| P4 | BCrypt password hashing | admin-service, api-gateway | Good |
| P5 | Stateless session management | admin-service | Good |
| P6 | Generic error messages (order/payment services) | order, payment | Good |
| P7 | CORS restricted to known origins | admin-service | Good |

---

## 9. Appendix: Files Reviewed

| File | Service | Security Relevance |
|------|---------|-------------------|
| `SecurityConfig.java` | admin-service | Auth filter chain, CORS, session management |
| `SecurityConfig.java` | api-gateway | Exchange authorization (permitAll) |
| `JwtTokenProvider.java` | admin-service | Token generation, validation, exception handling |
| `JwtAuthenticationFilter.java` | admin-service | JWT extraction from request |
| `ApiKeyAuthFilter.java` | api-gateway | API Key authentication, route bypass list |
| `GlobalExceptionHandler.java` | order-service | Error response format |
| `GlobalExceptionHandler.java` | payment-service | Error response format |
| `GlobalExceptionHandler.java` | admin-service | Error response format (finding F1) |
| `OrderRepository.java` | order-service | Query parameterization |
| `OrderController.java` | order-service | Request parameter handling |
| `docker-compose.always-on.yml` | infrastructure | Port exposure, env vars, resource limits |
| `pom.xml` (root) | all | Dependency versions |
