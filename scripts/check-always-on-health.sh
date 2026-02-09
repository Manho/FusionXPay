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

echo "[INFO] One-shot memory usage snapshot"
docker stats --no-stream \
  fusionxpay-api-gateway \
  fusionxpay-payment-service \
  fusionxpay-order-service \
  fusionxpay-notification-service \
  fusionxpay-admin-service
