#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.always-on.yml"
ENV_FILE="${1:-${ROOT_DIR}/.env.always-on}"
DEPLOY_LOG_FILE="$(mktemp "${TMPDIR:-/tmp}/fusionxpay-deploy.XXXXXX.log")"

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

is_transient_registry_error() {
  local log_file="$1"
  # Common transient network/registry issues observed on self-hosted runners.
  grep -Eiq \
    'failed to copy:|httpReadSeeker: failed|unexpected EOF|(^|[^a-z])EOF([^a-z]|$)|tls handshake timeout|connection reset by peer|i/o timeout|temporary failure in name resolution|net/http: request canceled|503 Service Unavailable|429 Too Many Requests' \
    "${log_file}"
}

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

if ! grep -Eq '^[[:space:]]*CORS_ALLOWED_ORIGINS=' "${ENV_FILE}"; then
  echo "[ERROR] Missing CORS_ALLOWED_ORIGINS in env file: ${ENV_FILE}"
  echo "Set CORS_ALLOWED_ORIGINS to comma-separated allowed frontend origins before deploy."
  exit 1
fi
echo "[INFO] CORS_ALLOWED_ORIGINS is configured in env file."

compose config >/dev/null

# --- Eureka cleanup: deregister stale instances before redeployment ---
EUREKA_URL=$(grep -oP '(?<=EUREKA_URL=).*' "${ENV_FILE}" | head -1)
if [[ -n "${EUREKA_URL}" ]]; then
  EUREKA_BASE="${EUREKA_URL%/eureka}/eureka"
  echo "[INFO] Cleaning stale Eureka registrations..."
  SERVICES=("API-GATEWAY" "PAYMENT-SERVICE" "ORDER-SERVICE" "NOTIFICATION-SERVICE" "ADMIN-SERVICE")
  for svc in "${SERVICES[@]}"; do
    # Fetch all instance IDs for this service
    instance_ids=$(curl -sf -H "Accept: application/json" "${EUREKA_BASE}/apps/${svc}" 2>/dev/null \
      | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    instances = data.get('application', {}).get('instance', [])
    if isinstance(instances, dict): instances = [instances]
    for inst in instances:
        print(inst.get('instanceId', ''))
except: pass
" 2>/dev/null || true)

    for iid in ${instance_ids}; do
      if [[ -n "${iid}" ]]; then
        curl -sf -X DELETE "${EUREKA_BASE}/apps/${svc}/${iid}" >/dev/null 2>&1 && \
          echo "[INFO]   Deregistered ${svc}/${iid}" || true
      fi
    done
  done
  echo "[INFO] Eureka cleanup complete. Services will re-register with correct hostnames on startup."
fi

echo "[INFO] Removing stale containers (if any)..."
for name in $(compose config --services); do
  cid=$(docker ps -aq -f "name=fusionxpay-${name}" 2>/dev/null || true)
  if [[ -n "${cid}" ]]; then
    echo "[INFO]   Removing stale container fusionxpay-${name} (${cid})"
    docker rm -f "${cid}" || true
  fi
done

echo "[INFO] Stopping existing project containers..."
compose down --timeout 30 --remove-orphans

echo "[INFO] Pulling upstream images (with retries on transient failures)..."
pull_attempt=1
pull_max_attempts="${DEPLOY_PULL_MAX_ATTEMPTS:-4}"
pull_backoff_seconds="${DEPLOY_PULL_BACKOFF_SECONDS:-3}"
while (( pull_attempt <= pull_max_attempts )); do
  if compose pull 2>&1 | tee "${DEPLOY_LOG_FILE}"; then
    break
  fi

  if is_transient_registry_error "${DEPLOY_LOG_FILE}"; then
    echo "[WARN] docker compose pull hit a transient registry/network error (attempt ${pull_attempt}/${pull_max_attempts})."
    sleep "${pull_backoff_seconds}"
    pull_backoff_seconds=$((pull_backoff_seconds * 2))
    pull_attempt=$((pull_attempt + 1))
    continue
  fi

  echo "[ERROR] docker compose pull failed for a non-transient reason."
  exit 1
done

if (( pull_attempt > pull_max_attempts )); then
  echo "[ERROR] docker compose pull failed after ${pull_max_attempts} attempts due to transient registry/network errors."
  exit 1
fi

echo "[INFO] Building and starting always-on services..."
up_attempt=1
up_max_attempts="${DEPLOY_UP_MAX_ATTEMPTS:-4}"
up_backoff_seconds="${DEPLOY_UP_BACKOFF_SECONDS:-3}"

while (( up_attempt <= up_max_attempts )); do
  if compose up -d --build --remove-orphans 2>&1 | tee "${DEPLOY_LOG_FILE}"; then
    break
  fi

  if grep -Eq 'container name "/[^"]+" is already in use' "${DEPLOY_LOG_FILE}"; then
    echo "[WARN] Container name conflict detected. Removing conflicted containers and retrying..."

    mapfile -t conflicted_containers < <(
      grep -Eo 'container name "/[^"]+" is already in use' "${DEPLOY_LOG_FILE}" \
        | grep -Eo '"/[^"]+"' \
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

    up_attempt=$((up_attempt + 1))
    continue
  fi

  if is_transient_registry_error "${DEPLOY_LOG_FILE}"; then
    echo "[WARN] docker compose up hit a transient registry/network error (attempt ${up_attempt}/${up_max_attempts})."
    sleep "${up_backoff_seconds}"
    up_backoff_seconds=$((up_backoff_seconds * 2))
    up_attempt=$((up_attempt + 1))
    continue
  fi

  echo "[ERROR] Deployment failed for a non-transient reason."
  exit 1
done

if (( up_attempt > up_max_attempts )); then
  echo "[ERROR] docker compose up failed after ${up_max_attempts} attempts due to transient registry/network errors."
  exit 1
fi

echo "[INFO] Deployment finished. Current status:"
compose ps
