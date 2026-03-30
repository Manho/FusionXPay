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
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"
AUTO_PROVISION_ADMIN="${AUTO_PROVISION_ADMIN:-true}"
DOCKER_RUNTIME_CHECKS="${DOCKER_RUNTIME_CHECKS:-auto}"

PASS=0
FAIL=0
WARN=0

# --- Helper Functions ---
log_info()  { echo -e "\033[34m[INFO]\033[0m  $*"; }
log_pass()  { echo -e "\033[32m[PASS]\033[0m  $*"; PASS=$((PASS+1)); }
log_fail()  { echo -e "\033[31m[FAIL]\033[0m  $*"; FAIL=$((FAIL+1)); }
log_warn()  { echo -e "\033[33m[WARN]\033[0m  $*"; WARN=$((WARN+1)); }
log_section() { echo -e "\n\033[1m=== $* ===\033[0m"; }

normalize_eureka_base() {
  local raw="$1"
  raw="${raw%/}"
  if [[ "$raw" == */eureka ]]; then
    echo "${raw%/eureka}"
  else
    echo "$raw"
  fi
}

json_field() {
  local payload="$1"
  local field="$2"
  python3 - <<'PY' "$payload" "$field"
import json
import sys

payload = sys.argv[1]
field = sys.argv[2]

try:
    obj = json.loads(payload)
except json.JSONDecodeError:
    print("")
    sys.exit(0)

cur = obj
for part in field.split("."):
    if isinstance(cur, dict) and part in cur:
        cur = cur[part]
    else:
        cur = None
        break

print("" if cur is None else cur)
PY
}

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

post_json() {
  local url="$1"
  local payload="$2"
  curl -s --connect-timeout 5 --max-time 20 \
    -X POST "$url" \
    -H "Content-Type: application/json" \
    -d "$payload" 2>/dev/null || echo "{}"
}

login_admin() {
  local email="$1"
  local password="$2"
  local response
  response=$(post_json "${BASE_URL}/api/v1/admin/auth/login" "{\"email\":\"${email}\",\"password\":\"${password}\"}")
  JWT_TOKEN="$(json_field "$response" "token")"
}

provision_temp_admin() {
  if [[ "${AUTO_PROVISION_ADMIN}" != "true" ]]; then
    return 1
  fi

  if ! command -v mysql >/dev/null 2>&1; then
    log_warn "mysql client not available — cannot auto-provision admin"
    return 1
  fi

  if [[ -z "${DB_HOST:-}" || -z "${DB_NAME:-}" || -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    log_warn "DB_* environment variables missing — cannot auto-provision admin"
    return 1
  fi

  local ts response merchant_email merchant_password merchant_id db_port mysql_status
  ts="$(date +%s)"
  merchant_email="verify-admin-${ts}@example.com"
  merchant_password="TestPass123!"

  response=$(post_json "${BASE_URL}/api/v1/admin/auth/register" "{
    \"merchantName\": \"Verify Admin ${ts}\",
    \"email\": \"${merchant_email}\",
    \"password\": \"${merchant_password}\"
  }")
  merchant_id="$(json_field "$response" "merchant.id")"
  if [[ -z "$merchant_id" ]]; then
    log_warn "Could not auto-register temporary admin merchant"
    return 1
  fi

  db_port="${DB_PORT:-3306}"
  mysql --host="${DB_HOST}" --port="${db_port}" --user="${DB_USERNAME}" --password="${DB_PASSWORD}" "${DB_NAME}" \
    -e "UPDATE merchant SET role='ADMIN' WHERE id=${merchant_id};" >/dev/null 2>&1 || mysql_status=$?

  if [[ -n "${mysql_status:-}" ]]; then
    log_warn "Could not promote temporary merchant to ADMIN in database"
    return 1
  fi

  ADMIN_EMAIL="${merchant_email}"
  ADMIN_PASSWORD="${merchant_password}"
  log_info "Provisioned temporary admin merchant: ${ADMIN_EMAIL}"
  return 0
}

EUREKA_BASE_URL="$(normalize_eureka_base "${EUREKA_URL}")"
EUREKA_APPS_URL="${EUREKA_BASE_URL}/eureka/apps"
LOCAL_CONTAINERS="fusionxpay-api-gateway fusionxpay-order-service fusionxpay-payment-service fusionxpay-notification-service fusionxpay-admin-service"

should_run_docker_checks() {
  case "${DOCKER_RUNTIME_CHECKS}" in
    true) return 0 ;;
    false) return 1 ;;
    auto)
      if ! command -v docker >/dev/null 2>&1; then
        return 1
      fi
      local container
      for container in ${LOCAL_CONTAINERS}; do
        if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${container}$"; then
          return 0
        fi
      done
      return 1
      ;;
    *)
      log_warn "Invalid DOCKER_RUNTIME_CHECKS=${DOCKER_RUNTIME_CHECKS}; expected true|false|auto"
      return 1
      ;;
  esac
}

# =============================================================================
log_section "1. Infrastructure Connectivity"
# =============================================================================

log_info "Checking middleware services..."

for svc in "Eureka:${EUREKA_APPS_URL}" \
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

EUREKA_APPS=$(curl -s -H "Accept: application/json" "${EUREKA_APPS_URL}" 2>/dev/null || echo "{}")

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
REGISTER_RESPONSE=$(post_json "${BASE_URL}/api/v1/admin/auth/register" "{
  \"merchantName\": \"Chain Test ${TIMESTAMP}\",
  \"email\": \"chaintest-${TIMESTAMP}@example.com\",
  \"password\": \"TestPass123!\"
}")

MERCHANT_JWT="$(json_field "$REGISTER_RESPONSE" "token")"

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
JWT_TOKEN=""

if [[ -n "$ADMIN_EMAIL" && -n "$ADMIN_PASSWORD" ]]; then
  login_admin "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
fi

if [[ -z "$JWT_TOKEN" ]]; then
  provision_temp_admin || true
fi

if [[ -n "$ADMIN_EMAIL" && -n "$ADMIN_PASSWORD" && -z "$JWT_TOKEN" ]]; then
  login_admin "$ADMIN_EMAIL" "$ADMIN_PASSWORD"
fi

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
  check_http "Gateway → Order Service (/api/v1/orders)" "200" \
    "${BASE_URL}/api/v1/orders" -H "Authorization: Bearer $MERCHANT_JWT"

  check_http "Gateway → Payment Service (/api/v1/payment/providers)" "200" \
    "${BASE_URL}/api/v1/payment/providers" -H "Authorization: Bearer $MERCHANT_JWT"
fi

if [[ -n "$JWT_TOKEN" ]]; then
  check_http "Gateway → Admin Service (/api/v1/admin/orders)" "200" \
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

  ORDER_ID="$(json_field "$ORDER_RESPONSE" "id")"
  if [[ -z "$ORDER_ID" ]]; then
    ORDER_ID="$(json_field "$ORDER_RESPONSE" "orderId")"
  fi

  if [[ -n "$ORDER_ID" ]]; then
    log_pass "Order created: $ORDER_ID"

    RETURN_URL="https://fusionx.fun/payment/success?orderId=${ORDER_ID}"
    CANCEL_URL="https://fusionx.fun/payment/cancel?orderId=${ORDER_ID}"
    PAYMENT_CODE=$(get_http_code \
      -X POST "${BASE_URL}/api/v1/payment/request" \
      -H "Authorization: Bearer $MERCHANT_JWT" \
      -H "Content-Type: application/json" \
      -d "{
        \"orderId\": \"$ORDER_ID\",
        \"amount\": 10.00,
        \"currency\": \"USD\",
        \"paymentChannel\": \"STRIPE\",
        \"description\": \"Chain verification payment\",
        \"returnUrl\": \"${RETURN_URL}\",
        \"cancelUrl\": \"${CANCEL_URL}\"
      }")

    if [[ "$PAYMENT_CODE" == "200" ]]; then
      log_pass "Payment → Order chain reachable (HTTP $PAYMENT_CODE)"
    elif [[ "$PAYMENT_CODE" == "000" ]]; then
      log_fail "Payment → Order chain — connection failed"
    else
      log_fail "Payment → Order chain returned unexpected HTTP $PAYMENT_CODE"
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

if should_run_docker_checks; then
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
else
  log_info "Skipping Kafka docker checks in local/NAS-backed mode"
fi

# =============================================================================
log_section "8. Container Health Status"
# =============================================================================

if should_run_docker_checks; then
  for container in ${LOCAL_CONTAINERS}; do
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
else
  log_info "Skipping Docker container health checks in local/NAS-backed mode"
fi

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
