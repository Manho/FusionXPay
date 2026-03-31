#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.always-on.yml"
ENV_FILE="${1:-${ROOT_DIR}/.env.always-on}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  exit 1
fi

API_PORT="${API_GATEWAY_PORT:-8080}"
if grep -q '^API_GATEWAY_PORT=' "${ENV_FILE}"; then
  API_PORT="$(grep '^API_GATEWAY_PORT=' "${ENV_FILE}" | cut -d'=' -f2)"
fi

HEALTH_URL="http://localhost:${API_PORT}/actuator/health"
GATEWAY_BASE_URL="http://localhost:${API_PORT}"
MAX_RETRIES=30
SLEEP_SECONDS=5
CORE_CONTAINERS=(
  fusionxpay-api-gateway
  fusionxpay-admin-service
  fusionxpay-order-service
  fusionxpay-payment-service
  fusionxpay-notification-service
)

wait_for_container_health() {
  local container="$1"
  local max_retries="$2"
  local sleep_seconds="$3"
  local status=""

  echo "[INFO] Waiting for container health: ${container}"
  for ((i=1; i<=max_retries; i++)); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "${container}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" ]]; then
      echo "[INFO] ${container} is healthy (attempt ${i}/${max_retries})"
      return 0
    fi
    if [[ "${i}" -eq "${max_retries}" ]]; then
      echo "[ERROR] ${container} did not become healthy. Last status: ${status:-missing}"
      return 1
    fi
    sleep "${sleep_seconds}"
  done
}

echo "[INFO] Waiting for gateway health: ${HEALTH_URL}"
for ((i=1; i<=MAX_RETRIES; i++)); do
  if curl -fsS "${HEALTH_URL}" >/dev/null; then
    echo "[INFO] Gateway health check passed (attempt ${i}/${MAX_RETRIES})"
    break
  fi
  if [[ "${i}" -eq "${MAX_RETRIES}" ]]; then
    echo "[ERROR] Gateway health check failed after ${MAX_RETRIES} attempts."
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
    exit 1
  fi
  sleep "${SLEEP_SECONDS}"
done

echo "[INFO] Container status"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps

for container in "${CORE_CONTAINERS[@]}"; do
  wait_for_container_health "${container}" 24 5
done

echo "[INFO] Running authenticated gateway smoke checks"
SMOKE_EMAIL="${ALWAYS_ON_SMOKE_EMAIL:-alwayson-smoke@fusionxpay.test}"
SMOKE_PASSWORD="${ALWAYS_ON_SMOKE_PASSWORD:-SmokePass123!}"
SMOKE_MERCHANT_NAME="${ALWAYS_ON_SMOKE_MERCHANT_NAME:-Always On Smoke Test}"
AUTH_MAX_RETRIES=12
SMOKE_TOKEN=""

extract_token() {
  python3 -c '
import json, sys
data = json.load(sys.stdin)
print(data.get("token", ""))
' 
}

login_smoke_account() {
  curl -sS -o /tmp/fusionxpay-alwayson-login-smoke.out -w '%{http_code}' \
    -X POST "${GATEWAY_BASE_URL}/api/v1/admin/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\":\"${SMOKE_EMAIL}\",
      \"password\":\"${SMOKE_PASSWORD}\"
    }"
}

register_smoke_account() {
  curl -sS -o /tmp/fusionxpay-alwayson-register-smoke.out -w '%{http_code}' \
    -X POST "${GATEWAY_BASE_URL}/api/v1/admin/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\":\"${SMOKE_EMAIL}\",
      \"password\":\"${SMOKE_PASSWORD}\",
      \"merchantName\":\"${SMOKE_MERCHANT_NAME}\"
    }"
}

for ((i=1; i<=AUTH_MAX_RETRIES; i++)); do
  LOGIN_HTTP_STATUS="$(login_smoke_account)"
  if [[ "${LOGIN_HTTP_STATUS}" == "200" ]]; then
    SMOKE_TOKEN="$(cat /tmp/fusionxpay-alwayson-login-smoke.out | extract_token)"
    break
  fi

  REGISTER_HTTP_STATUS="$(register_smoke_account)"
  if [[ "${REGISTER_HTTP_STATUS}" == "200" ]]; then
    SMOKE_TOKEN="$(cat /tmp/fusionxpay-alwayson-register-smoke.out | extract_token)"
    break
  fi

  if [[ "${REGISTER_HTTP_STATUS}" == "409" || "${REGISTER_HTTP_STATUS}" == "400" ]]; then
    LOGIN_HTTP_STATUS="$(login_smoke_account)"
    if [[ "${LOGIN_HTTP_STATUS}" == "200" ]]; then
      SMOKE_TOKEN="$(cat /tmp/fusionxpay-alwayson-login-smoke.out | extract_token)"
      break
    fi
  fi

  if [[ "${i}" -eq "${AUTH_MAX_RETRIES}" ]]; then
    echo "[ERROR] Failed to authenticate smoke account after ${AUTH_MAX_RETRIES} attempts."
    echo "[ERROR] Last login status: ${LOGIN_HTTP_STATUS:-unknown}"
    cat /tmp/fusionxpay-alwayson-login-smoke.out 2>/dev/null || true
    echo "[ERROR] Last register status: ${REGISTER_HTTP_STATUS:-unknown}"
    cat /tmp/fusionxpay-alwayson-register-smoke.out 2>/dev/null || true
    exit 1
  fi

  echo "[WARN] Smoke account auth failed (login=${LOGIN_HTTP_STATUS:-n/a}, register=${REGISTER_HTTP_STATUS:-n/a}); retrying (${i}/${AUTH_MAX_RETRIES})..."
  sleep 5
done

if [[ -z "${SMOKE_TOKEN}" ]]; then
  echo "[ERROR] Failed to obtain JWT token for smoke account."
  exit 1
fi

ORDERS_STATUS="$(curl -s -o /tmp/fusionxpay-alwayson-orders-smoke.out -w '%{http_code}' \
  -H "Authorization: Bearer ${SMOKE_TOKEN}" \
  "${GATEWAY_BASE_URL}/api/v1/orders")"
if [[ "${ORDERS_STATUS}" != "200" ]]; then
  echo "[ERROR] Authenticated orders smoke check failed with status ${ORDERS_STATUS}."
  cat /tmp/fusionxpay-alwayson-orders-smoke.out
  exit 1
fi

PAYMENT_PROVIDERS_STATUS="$(curl -s -o /tmp/fusionxpay-alwayson-providers-smoke.out -w '%{http_code}' \
  -H "Authorization: Bearer ${SMOKE_TOKEN}" \
  "${GATEWAY_BASE_URL}/api/v1/payment/providers")"
if [[ "${PAYMENT_PROVIDERS_STATUS}" != "200" ]]; then
  echo "[ERROR] Authenticated payment providers smoke check failed with status ${PAYMENT_PROVIDERS_STATUS}."
  cat /tmp/fusionxpay-alwayson-providers-smoke.out
  exit 1
fi

rm -f /tmp/fusionxpay-alwayson-login-smoke.out /tmp/fusionxpay-alwayson-register-smoke.out /tmp/fusionxpay-alwayson-orders-smoke.out /tmp/fusionxpay-alwayson-providers-smoke.out

echo "[INFO] One-shot memory usage snapshot"
docker stats --no-stream \
  fusionxpay-api-gateway \
  fusionxpay-payment-service \
  fusionxpay-order-service \
  fusionxpay-notification-service \
  fusionxpay-admin-service
