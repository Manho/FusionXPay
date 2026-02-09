#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_INPUT="${1:-${ROOT_DIR}/.env.always-on}"

if [[ "${ENV_INPUT}" = /* ]]; then
  ENV_FILE="${ENV_INPUT}"
else
  ENV_FILE="${ROOT_DIR}/${ENV_INPUT#./}"
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  exit 1
fi

echo "[INFO] Deploying revision: $(git rev-parse --short HEAD)"
"${ROOT_DIR}/scripts/deploy-always-on.sh" "${ENV_FILE}"
