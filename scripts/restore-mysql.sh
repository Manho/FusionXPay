#!/usr/bin/env bash
set -euo pipefail

if (( $# < 1 )); then
  echo "Usage: $0 <backup-file(.sql|.sql.gz)> [env-file]"
  echo "Set RESTORE_CONFIRM=YES to proceed."
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_INPUT="${1}"
ENV_INPUT="${2:-${ROOT_DIR}/.env.always-on}"

if [[ "${BACKUP_INPUT}" = /* ]]; then
  BACKUP_FILE="${BACKUP_INPUT}"
else
  BACKUP_FILE="${ROOT_DIR}/${BACKUP_INPUT#./}"
fi

if [[ "${ENV_INPUT}" = /* ]]; then
  ENV_FILE="${ENV_INPUT}"
else
  ENV_FILE="${ROOT_DIR}/${ENV_INPUT#./}"
fi

if [[ "${RESTORE_CONFIRM:-}" != "YES" ]]; then
  echo "[ERROR] Restore requires explicit confirmation: RESTORE_CONFIRM=YES"
  exit 1
fi

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "[ERROR] Backup file not found: ${BACKUP_FILE}"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

required_vars=(DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD)
for key in "${required_vars[@]}"; do
  if [[ -z "${!key:-}" ]]; then
    echo "[ERROR] ${key} is required in ${ENV_FILE}"
    exit 1
  fi
done

echo "[INFO] Restoring database ${DB_NAME} from ${BACKUP_FILE}"
if [[ "${BACKUP_FILE}" == *.gz ]]; then
  gzip -dc "${BACKUP_FILE}" | MYSQL_PWD="${DB_PASSWORD}" mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}"
else
  MYSQL_PWD="${DB_PASSWORD}" mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}" < "${BACKUP_FILE}"
fi

echo "[INFO] Restore completed."
