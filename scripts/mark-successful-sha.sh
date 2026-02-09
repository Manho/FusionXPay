#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${DEPLOY_STATE_DIR:-$HOME/.fusionxpay-deploy}"
LAST_GOOD_FILE="${STATE_DIR}/last_successful_sha"

mkdir -p "${STATE_DIR}"
cd "${ROOT_DIR}"
git rev-parse HEAD > "${LAST_GOOD_FILE}"

echo "[INFO] Marked successful revision: $(cat "${LAST_GOOD_FILE}")"
echo "[INFO] State file: ${LAST_GOOD_FILE}"
