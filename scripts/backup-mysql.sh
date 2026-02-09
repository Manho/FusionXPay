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

BACKUP_DIR="${BACKUP_DIR:-${ROOT_DIR}/backups/mysql}"
RETENTION="${BACKUP_RETENTION_COUNT:-14}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_FILE="${DB_NAME}_${TIMESTAMP}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Writing backup to ${BACKUP_PATH}"
MYSQL_PWD="${DB_PASSWORD}" mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --single-transaction \
  --routines \
  --triggers \
  --databases "${DB_NAME}" | gzip -9 > "${BACKUP_PATH}"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${BACKUP_PATH}"
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "${BACKUP_PATH}"
fi

mapfile -t backup_files < <(find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" | sort)
if (( ${#backup_files[@]} > RETENTION )); then
  delete_count=$(( ${#backup_files[@]} - RETENTION ))
  for ((i=0; i<delete_count; i++)); do
    rm -f "${backup_files[$i]}"
    echo "[INFO] Removed old backup: ${backup_files[$i]}"
  done
fi

ln -sfn "${BACKUP_FILE}" "${BACKUP_DIR}/${DB_NAME}_latest.sql.gz"
echo "[INFO] Backup completed: ${BACKUP_PATH}"
