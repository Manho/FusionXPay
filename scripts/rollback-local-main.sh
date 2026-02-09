#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_INPUT="${1:-${ROOT_DIR}/.env.always-on}"
STATE_DIR="${DEPLOY_STATE_DIR:-$HOME/.fusionxpay-deploy}"
LAST_GOOD_FILE="${STATE_DIR}/last_successful_sha"

if [[ "${ENV_INPUT}" = /* ]]; then
  ENV_FILE="${ENV_INPUT}"
else
  ENV_FILE="${ROOT_DIR}/${ENV_INPUT#./}"
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file for rollback: ${ENV_FILE}"
  exit 1
fi

if [[ ! -f "${LAST_GOOD_FILE}" ]]; then
  echo "[WARN] No previous successful deployment recorded. Skip rollback."
  exit 0
fi

TARGET_SHA="$(tr -d '[:space:]' < "${LAST_GOOD_FILE}")"
if [[ -z "${TARGET_SHA}" ]]; then
  echo "[WARN] Last successful SHA is empty. Skip rollback."
  exit 0
fi

cd "${ROOT_DIR}"

if ! git cat-file -e "${TARGET_SHA}^{commit}" 2>/dev/null; then
  echo "[INFO] Revision ${TARGET_SHA} not found locally. Fetching origin..."
  git fetch --prune origin
fi

if ! git cat-file -e "${TARGET_SHA}^{commit}" 2>/dev/null; then
  echo "[ERROR] Cannot locate rollback revision: ${TARGET_SHA}"
  exit 1
fi

ROLLBACK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fusionxpay-rollback.XXXXXX")"
cleanup() {
  git worktree remove --force "${ROLLBACK_DIR}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

git worktree add --detach "${ROLLBACK_DIR}" "${TARGET_SHA}" >/dev/null

echo "[INFO] Rolling back to revision: ${TARGET_SHA}"
"${ROLLBACK_DIR}/scripts/deploy-always-on.sh" "${ENV_FILE}"
"${ROLLBACK_DIR}/scripts/check-always-on-health.sh" "${ENV_FILE}"
echo "[INFO] Rollback completed successfully."
