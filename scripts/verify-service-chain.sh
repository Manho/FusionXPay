#!/usr/bin/env bash
# =============================================================================
# FusionXPay Service Chain Verification Script
# Validates end-to-end service connectivity and message flow
# =============================================================================
set -euo pipefail

# --- Configuration ---
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_HOST="${API_HOST:-localhost}"
API_PORT="${API_PORT:-8080}"
BASE_URL="http://${API_HOST}:${API_PORT}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"

PASS=0
FAIL=0
WARN=0

# --- Helper Functions ---
log_info()  { echo -e "\033[34m[INFO]\033[0m  $*"; }
log_pass()  { echo -e "\033[32m[PASS]\033[0m  $*"; PASS=$((PASS+1)); }
log_fail()  { echo -e "\033[31m[FAIL]\033[0m  $*"; FAIL=$((FAIL+1)); }
log_warn()  { echo -e "\033[33m[WARN]\033[0m  $*"; WARN=$((WARN+1)); }
log_section() { echo -e "\n\033[1m=== $* ===\033[0m"; }

get_http_code() {
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 --max-time 10 "$@" 2>/dev/null) || true
  # curl outputs "000" on connection failure; guard against unexpected output
  if [[ ! "$code" =~ ^[0-9]{3}$ ]]; then
    code="000"
  fi
  echo "$code"
}

check_http() {
  local description="$1"
  local expected_code="$2"
  local url="$3"
  shift 3
  local actual_code
  actual_code=$(get_http_code "$@" "$url")
  if [[ "$actual_code" == "$expected_code" ]]; then
    log_pass "$description (HTTP $actual_code)"
  else
    log_fail "$description — expected $expected_code, got $actual_code"
  fi
}

check_http_not() {
  local description="$1"
  local unexpected_code="$2"
  local url="$3"
  shift 3
  local actual_code
  actual_code=$(get_http_code "$@" "$url")
  if [[ "$actual_code" == "000" ]]; then
    log_fail "$description — connection failed"
  elif [[ "$actual_code" != "$unexpected_code" ]]; then
    log_pass "$description (HTTP $actual_code, not $unexpected_code)"
  else
    log_fail "$description — got $actual_code"
  fi
}

# =============================================================================
log_section "1. Infrastructure Connectivity"
# =============================================================================

log_info "Checking middleware services..."

for svc in "Eureka:${EUREKA_URL}/eureka/apps" \
           "API Gateway:${BASE_URL}/actuator/health"; do
  name="${svc%%:*}"
  url="${svc#*:}"
  code=$(get_http_code "$url")
  if [[ "$code" == "200" ]]; then
    log_pass "$name is reachable (HTTP $code)"
  else
    log_fail "$name is unreachable (HTTP $code)"
  fi
done

# =============================================================================
log_section "2. Eureka Service Registration"
# =============================================================================

log_info "Checking registered services in Eureka..."

EUREKA_APPS=$(curl -s -H "Accept: application/json" "${EUREKA_URL}/eureka/apps" 2>/dev/null || echo "{}")

for svc in API-GATEWAY ORDER-SERVICE PAYMENT-SERVICE NOTIFICATION-SERVICE ADMIN-SERVICE; do
  if echo "$EUREKA_APPS" | grep -qi "\"$svc\""; then
    log_pass "$svc registered in Eureka"
  else
    log_fail "$svc NOT registered in Eureka"
  fi
done

# =============================================================================
log_section "3. Service Health Checks (via Gateway)"
# =============================================================================

check_http "API Gateway health" "200" "${BASE_URL}/actuator/health"

# =============================================================================
log_section "4. Authentication Chain"
# =============================================================================

log_info "Testing JWT authentication..."

# 4.1 No JWT → 401
check_http "No JWT → 401" "401" "${BASE_URL}/api/v1/orders"

# 4.2 Invalid JWT → 401
check_http "Invalid JWT → 401" "401" "${BASE_URL}/api/v1/orders" \
  -H "Authorization: Bearer invalid-jwt-token"

# 4.3 Register merchant and get JWT
log_info "Registering test merchant to obtain JWT..."
TIMESTAMP=$(date +%s)
REGISTER_RESPONSE=$(curl -s --connect-timeout 5 --max-time 10 \
  -X POST "${BASE_URL}/api/v1/admin/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"merchantName\": \"Chain Test ${TIMESTAMP}\",
    \"email\": \"chaintest-${TIMESTAMP}@example.com\",
    \"password\": \"TestPass123!\"
  }" 2>/dev/null || echo "{}")

MERCHANT_JWT=$(echo "$REGISTER_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

if [[ -n "$MERCHANT_JWT" ]]; then
  log_pass "Merchant registered, JWT obtained"
else
  log_fail "Could not register merchant — all chain tests requiring JWT will fail"
  MERCHANT_JWT=""
fi

# 4.4 Valid JWT → should work
if [[ -n "$MERCHANT_JWT" ]]; then
  check_http_not "Valid JWT → not 401" "401" "${BASE_URL}/api/v1/orders" \
    -H "Authorization: Bearer $MERCHANT_JWT"
fi

# 4.5 Admin login
log_info "Testing admin authentication..."
LOGIN_RESPONSE=$(curl -s --connect-timeout 5 --max-time 10 \
  -X POST "${BASE_URL}/api/v1/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@fusionxpay.com","password":"admin123"}' 2>/dev/null || echo "{}")

JWT_TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

if [[ -n "$JWT_TOKEN" ]]; then
  log_pass "Admin login successful, JWT obtained"
else
  log_warn "Admin login failed — admin endpoint tests will be skipped"
fi

# =============================================================================
log_section "5. API Gateway → Service Routing"
# =============================================================================

log_info "Verifying gateway routes to downstream services..."

if [[ -n "$MERCHANT_JWT" ]]; then
  # Orders route
  check_http_not "Gateway → Order Service (/api/v1/orders)" "502" \
    "${BASE_URL}/api/v1/orders" -H "Authorization: Bearer $MERCHANT_JWT"

  # Payment route: prefer a simple read-only endpoint so this check doesn't depend on provider secrets.
  check_http_not "Gateway → Payment Service (/api/v1/payment)" "502" \
    "${BASE_URL}/api/v1/payment/providers" -H "Authorization: Bearer $MERCHANT_JWT"
fi

if [[ -n "$JWT_TOKEN" ]]; then
  # Admin route
  check_http_not "Gateway → Admin Service (/api/v1/admin)" "502" \
    "${BASE_URL}/api/v1/admin/orders" \
    -H "Authorization: Bearer $JWT_TOKEN"
fi

# =============================================================================
log_section "6. Cross-Service Call: Payment → Order (Feign)"
# =============================================================================

if [[ -n "$MERCHANT_JWT" ]]; then
  log_info "Creating an order to test payment → order chain..."

  ORDER_RESPONSE=$(curl -s --connect-timeout 5 --max-time 15 \
    -X POST "${BASE_URL}/api/v1/orders" \
    -H "Authorization: Bearer $MERCHANT_JWT" \
    -H "Content-Type: application/json" \
    -d "{
      \"amount\": 10.00,
      \"currency\": \"USD\",
      \"description\": \"Chain verification test order\"
    }" 2>/dev/null || echo "{}")

  ORDER_ID=$(echo "$ORDER_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4 || \
             echo "$ORDER_RESPONSE" | grep -o '"orderId":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

  if [[ -n "$ORDER_ID" ]]; then
    log_pass "Order created: $ORDER_ID"

    # Initiate payment (will likely fail since Stripe isn't configured, but tests the route)
    PAYMENT_CODE=$(get_http_code \
      -X POST "${BASE_URL}/api/v1/payment/request" \
      -H "Authorization: Bearer $MERCHANT_JWT" \
      -H "Content-Type: application/json" \
      -d "{
        \"orderId\": \"$ORDER_ID\",
        \"amount\": 10.00,
        \"currency\": \"USD\",
        \"paymentChannel\": \"STRIPE\"
      }")

    if [[ "$PAYMENT_CODE" == "000" ]]; then
      log_fail "Payment → Order chain — connection failed"
    elif [[ "$PAYMENT_CODE" != "502" ]]; then
      log_pass "Payment → Order chain reachable (HTTP $PAYMENT_CODE)"
    else
      log_fail "Payment → Order chain unreachable (HTTP $PAYMENT_CODE)"
    fi
  else
    log_fail "Could not create order — payment chain test cannot proceed"
  fi
else
  log_warn "No JWT — skipping cross-service call tests"
fi

# =============================================================================
log_section "7. Kafka Message Flow Verification"
# =============================================================================

log_info "Kafka verification requires docker exec access..."

# Check if we can reach the Kafka container
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "fusionxpay-kafka\|kafka"; then
  KAFKA_CONTAINER=$(docker ps --format '{{.Names}}' 2>/dev/null | grep -E "fusionxpay-kafka|kafka" | head -1)

  # List topics
  TOPICS=$(docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:29092 --list 2>/dev/null || \
           docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")

  if echo "$TOPICS" | grep -q "payment-events"; then
    log_pass "Kafka topic 'payment-events' exists"
  else
    log_fail "Kafka topic 'payment-events' not found"
  fi

  if echo "$TOPICS" | grep -q "order-events"; then
    log_pass "Kafka topic 'order-events' exists"
  else
    log_warn "Kafka topic 'order-events' not found (may be auto-created on first message)"
  fi

  # Check consumer groups
  GROUPS=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups --bootstrap-server localhost:29092 --list 2>/dev/null || \
           docker exec "$KAFKA_CONTAINER" kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")

  if echo "$GROUPS" | grep -q "order-service-group"; then
    log_pass "Consumer group 'order-service-group' active"
  else
    log_warn "Consumer group 'order-service-group' not found (may start on first message)"
  fi

  if echo "$GROUPS" | grep -q "notification-service"; then
    log_pass "Consumer group 'notification-service' active"
  else
    log_warn "Consumer group 'notification-service' not found"
  fi
else
  log_warn "Kafka container not accessible — skipping Kafka checks"
fi

# =============================================================================
log_section "8. Container Health Status"
# =============================================================================

CONTAINERS="fusionxpay-api-gateway fusionxpay-order-service fusionxpay-payment-service fusionxpay-notification-service fusionxpay-admin-service"

for container in $CONTAINERS; do
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${container}$"; then
    health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container" 2>/dev/null || echo "unknown")
    if [[ "$health" == "healthy" ]]; then
      log_pass "$container is healthy"
    elif [[ "$health" == "starting" ]]; then
      log_warn "$container is still starting"
    elif [[ "$health" == "no-healthcheck" ]]; then
      log_warn "$container has no Docker healthcheck configured"
    else
      log_fail "$container health: $health"
    fi
  else
    log_warn "$container not running"
  fi
done

# =============================================================================
log_section "Summary"
# =============================================================================

TOTAL=$((PASS + FAIL + WARN))
echo ""
echo "  Total checks: $TOTAL"
echo -e "  \033[32mPASS: $PASS\033[0m"
echo -e "  \033[31mFAIL: $FAIL\033[0m"
echo -e "  \033[33mWARN: $WARN\033[0m"
echo ""

if [[ $FAIL -gt 0 ]]; then
  echo -e "\033[31mChain verification completed with failures.\033[0m"
  exit 1
else
  echo -e "\033[32mChain verification completed successfully.\033[0m"
  exit 0
fi
