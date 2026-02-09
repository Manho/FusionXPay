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

echo "[INFO] Building and starting always-on services..."
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build --remove-orphans

echo "[INFO] Deployment finished. Current status:"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
