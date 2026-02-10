#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.always-on.yml"
ENV_FILE="${1:-${ROOT_DIR}/.env.always-on}"
DEPLOY_LOG_FILE="$(mktemp "${TMPDIR:-/tmp}/fusionxpay-deploy.XXXXXX.log")"

cleanup() {
  rm -f "${DEPLOY_LOG_FILE}"
}
trap cleanup EXIT

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  echo "Copy .env.always-on.example to .env.always-on and fill real values first."
  exit 1
fi

echo "[INFO] Using compose file: ${COMPOSE_FILE}"
echo "[INFO] Using env file: ${ENV_FILE}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config >/dev/null

echo "[INFO] Building and starting always-on services..."
if ! docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build --remove-orphans 2>&1 | tee "${DEPLOY_LOG_FILE}"; then
  if grep -Eq 'container name "/[^"]+" is already in use' "${DEPLOY_LOG_FILE}"; then
    echo "[WARN] Container name conflict detected. Removing conflicted containers and retrying once..."

    mapfile -t conflicted_containers < <(
      grep -E 'container name "/[^"]+" is already in use' "${DEPLOY_LOG_FILE}" \
        | grep -oE '"/[^"]+"' \
        | tr -d '"' \
        | sed 's#^/##' \
        | sort -u
    )

    if [[ "${#conflicted_containers[@]}" -eq 0 ]]; then
      echo "[ERROR] Could not parse conflicted container names from Docker output."
      exit 1
    fi

    for container in "${conflicted_containers[@]}"; do
      echo "[INFO] Removing stale container: ${container}"
      docker rm -f "${container}" >/dev/null || true
    done

    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build --remove-orphans
  else
    echo "[ERROR] Deployment failed for a non-container-name-conflict reason."
    exit 1
  fi
fi

echo "[INFO] Deployment finished. Current status:"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
