#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.always-on.yml"
ENV_FILE="${1:-${ROOT_DIR}/.env.always-on}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  echo "Copy .env.always-on.example to .env.always-on and fill real values first."
  exit 1
fi

echo "[INFO] Using compose file: ${COMPOSE_FILE}"
echo "[INFO] Using env file: ${ENV_FILE}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config >/dev/null

echo "[INFO] Removing stale containers (if any)..."
for name in $(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config --services); do
  cid=$(docker ps -aq -f "name=fusionxpay-${name}" 2>/dev/null || true)
  if [[ -n "${cid}" ]]; then
    echo "[INFO]   Removing stale container fusionxpay-${name} (${cid})"
    docker rm -f "${cid}" || true
  fi
done

echo "[INFO] Stopping existing project containers..."
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" down --timeout 30 --remove-orphans

echo "[INFO] Building and starting always-on services..."
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build --remove-orphans

echo "[INFO] Deployment finished. Current status:"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
