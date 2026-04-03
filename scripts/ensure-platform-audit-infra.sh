#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_ONLY=false
ENV_INPUT="${ROOT_DIR}/.env.always-on"

if [[ "${1:-}" == "--verify-only" ]]; then
  VERIFY_ONLY=true
  shift
fi

if [[ "${1:-}" != "" ]]; then
  ENV_INPUT="$1"
fi

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

DB_HOST="${DB_HOST:?DB_HOST is required}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-fusionxpay_db}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME is required}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"
FUSIONX_PLATFORM_AUDIT_TOPIC="${FUSIONX_PLATFORM_AUDIT_TOPIC:-platform-audit-log}"
PLATFORM_AUDIT_TABLE_NAME="${PLATFORM_AUDIT_TABLE_NAME:-platform_audit_log}"
PLATFORM_AUDIT_SINK_ENABLED="${PLATFORM_AUDIT_SINK_ENABLED:-true}"
KAFKA_CONNECT_PORT="${KAFKA_CONNECT_PORT:-8083}"
KAFKA_CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:${KAFKA_CONNECT_PORT}}"
PLATFORM_AUDIT_CONNECTOR_NAME="${PLATFORM_AUDIT_CONNECTOR_NAME:-platform-audit-log-mysql-sink}"
SCHEMA_FILE="${ROOT_DIR}/mysql-init/07-platform-audit-log.sql"
CONNECTOR_TEMPLATE="${ROOT_DIR}/ops/kafka-connect/platform-audit-sink.json"

if [[ ! -f "${SCHEMA_FILE}" ]]; then
  echo "[ERROR] Missing schema file: ${SCHEMA_FILE}"
  exit 1
fi

if [[ ! -f "${CONNECTOR_TEMPLATE}" ]]; then
  echo "[ERROR] Missing connector template: ${CONNECTOR_TEMPLATE}"
  exit 1
fi

run_mysql_query() {
  local sql="$1"
  if command -v mysql >/dev/null 2>&1; then
    mysql \
      --batch --skip-column-names \
      -h "${DB_HOST}" \
      -P "${DB_PORT}" \
      -u "${DB_USERNAME}" \
      "-p${DB_PASSWORD}" \
      "${DB_NAME}" \
      -e "${sql}"
    return
  fi

  docker run --rm -i mysql:8.4 \
    mysql \
    --batch --skip-column-names \
    -h "${DB_HOST}" \
    -P "${DB_PORT}" \
    -u "${DB_USERNAME}" \
    "-p${DB_PASSWORD}" \
    "${DB_NAME}" \
    -e "${sql}"
}

run_mysql_file() {
  local sql_file="$1"
  if command -v mysql >/dev/null 2>&1; then
    mysql \
      -h "${DB_HOST}" \
      -P "${DB_PORT}" \
      -u "${DB_USERNAME}" \
      "-p${DB_PASSWORD}" \
      "${DB_NAME}" < "${sql_file}"
    return
  fi

  docker run --rm -i mysql:8.4 \
    mysql \
    -h "${DB_HOST}" \
    -P "${DB_PORT}" \
    -u "${DB_USERNAME}" \
    "-p${DB_PASSWORD}" \
    "${DB_NAME}" < "${sql_file}"
}

table_exists() {
  local result
  result="$(run_mysql_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='${PLATFORM_AUDIT_TABLE_NAME}';")"
  [[ "${result}" == "1" ]]
}

wait_for_connect() {
  local max_retries="${1:-30}"
  local sleep_seconds="${2:-5}"
  for ((i=1; i<=max_retries; i++)); do
    if curl -fsS "${KAFKA_CONNECT_URL}/connectors" >/dev/null 2>&1; then
      echo "[INFO] Kafka Connect is reachable at ${KAFKA_CONNECT_URL} (attempt ${i}/${max_retries})"
      return 0
    fi
    if [[ "${i}" -eq "${max_retries}" ]]; then
      echo "[ERROR] Kafka Connect is unreachable at ${KAFKA_CONNECT_URL}"
      return 1
    fi
    sleep "${sleep_seconds}"
  done
}

render_connector_config() {
  python3 - <<'PY' "${CONNECTOR_TEMPLATE}" "${DB_HOST}" "${DB_PORT}" "${DB_NAME}" "${DB_USERNAME}" "${DB_PASSWORD}" "${FUSIONX_PLATFORM_AUDIT_TOPIC}" "${PLATFORM_AUDIT_TABLE_NAME}"
import json
import sys

template_path, db_host, db_port, db_name, db_user, db_password, topic, table_name = sys.argv[1:]
with open(template_path, "r", encoding="utf-8") as handle:
    template = json.load(handle)

config = template["config"]
config["topics"] = topic
config["table.name.format"] = table_name
config["connection.url"] = (
    f"jdbc:mysql://{db_host}:{db_port}/{db_name}"
    "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
)
config["connection.user"] = db_user
config["connection.password"] = db_password

print(json.dumps(config, separators=(",", ":")))
PY
}

upsert_connector() {
  local payload
  payload="$(render_connector_config)"
  curl -fsS \
    -X PUT \
    -H "Content-Type: application/json" \
    "${KAFKA_CONNECT_URL}/connectors/${PLATFORM_AUDIT_CONNECTOR_NAME}/config" \
    -d "${payload}" >/dev/null
}

connector_running() {
  local status_json connector_state task_states
  status_json="$(curl -fsS "${KAFKA_CONNECT_URL}/connectors/${PLATFORM_AUDIT_CONNECTOR_NAME}/status")"
  connector_state="$(python3 - <<'PY' "${status_json}"
import json
import sys
payload = json.loads(sys.argv[1])
print(payload.get("connector", {}).get("state", ""))
PY
)"
  task_states="$(python3 - <<'PY' "${status_json}"
import json
import sys
payload = json.loads(sys.argv[1])
states = [task.get("state", "") for task in payload.get("tasks", [])]
print(",".join(states))
PY
)"

  [[ "${connector_state}" == "RUNNING" ]] || return 1
  [[ -n "${task_states}" ]] || return 1
  [[ "${task_states}" != *FAILED* ]] || return 1
  [[ "${task_states}" != *UNASSIGNED* ]] || return 1
}

wait_for_connector_running() {
  local max_retries="${1:-30}"
  local sleep_seconds="${2:-5}"
  for ((i=1; i<=max_retries; i++)); do
    if connector_running; then
      echo "[INFO] Kafka Connect connector ${PLATFORM_AUDIT_CONNECTOR_NAME} is RUNNING (attempt ${i}/${max_retries})"
      return 0
    fi
    if [[ "${i}" -eq "${max_retries}" ]]; then
      echo "[ERROR] Kafka Connect connector ${PLATFORM_AUDIT_CONNECTOR_NAME} did not reach RUNNING state"
      curl -fsS "${KAFKA_CONNECT_URL}/connectors/${PLATFORM_AUDIT_CONNECTOR_NAME}/status" || true
      return 1
    fi
    sleep "${sleep_seconds}"
  done
}

echo "[INFO] Ensuring platform audit MySQL table ${PLATFORM_AUDIT_TABLE_NAME}"
if [[ "${VERIFY_ONLY}" == "true" ]]; then
  if ! table_exists; then
    echo "[ERROR] Missing MySQL table: ${PLATFORM_AUDIT_TABLE_NAME}"
    exit 1
  fi
else
  run_mysql_file "${SCHEMA_FILE}"
fi

if ! table_exists; then
  echo "[ERROR] Failed to verify MySQL table: ${PLATFORM_AUDIT_TABLE_NAME}"
  exit 1
fi

if [[ "${PLATFORM_AUDIT_SINK_ENABLED}" != "true" ]]; then
  echo "[INFO] PLATFORM_AUDIT_SINK_ENABLED=${PLATFORM_AUDIT_SINK_ENABLED}; skipping Kafka Connect sink setup"
  exit 0
fi

echo "[INFO] Ensuring platform audit Kafka Connect sink ${PLATFORM_AUDIT_CONNECTOR_NAME}"
wait_for_connect
if [[ "${VERIFY_ONLY}" == "false" ]]; then
  upsert_connector
fi
wait_for_connector_running
