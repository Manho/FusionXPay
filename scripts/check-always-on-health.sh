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

echo "[INFO] Running authenticated gateway smoke checks"
SMOKE_EMAIL="alwayson-smoke-$(date +%s)@fusionxpay.test"
SMOKE_PASSWORD="SmokePass123!"
REGISTER_HTTP_STATUS="$(curl -sS -o /tmp/fusionxpay-alwayson-register-smoke.out -w '%{http_code}' \
  -X POST "${GATEWAY_BASE_URL}/api/v1/admin/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email\":\"${SMOKE_EMAIL}\",
    \"password\":\"${SMOKE_PASSWORD}\",
    \"merchantName\":\"Always On Smoke Test\"
  }")"

if [[ "${REGISTER_HTTP_STATUS}" != "200" ]]; then
  echo "[ERROR] Authenticated register smoke check failed with status ${REGISTER_HTTP_STATUS}."
  cat /tmp/fusionxpay-alwayson-register-smoke.out
  exit 1
fi

REGISTER_RESPONSE="$(cat /tmp/fusionxpay-alwayson-register-smoke.out)"

SMOKE_TOKEN="$(printf '%s' "${REGISTER_RESPONSE}" | python3 -c '
import json, sys
data = json.load(sys.stdin)
print(data.get("token", ""))
')"

if [[ -z "${SMOKE_TOKEN}" ]]; then
  echo "[ERROR] Failed to obtain JWT token from registration response."
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

rm -f /tmp/fusionxpay-alwayson-register-smoke.out /tmp/fusionxpay-alwayson-orders-smoke.out /tmp/fusionxpay-alwayson-providers-smoke.out

echo "[INFO] One-shot memory usage snapshot"
docker stats --no-stream \
  fusionxpay-api-gateway \
  fusionxpay-payment-service \
  fusionxpay-order-service \
  fusionxpay-notification-service \
  fusionxpay-admin-service
