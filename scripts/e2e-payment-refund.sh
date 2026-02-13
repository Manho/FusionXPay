#!/usr/bin/env bash
set -euo pipefail

# E2E payment + refund smoke test (designed to run on the app host).
#
# Notes:
# - External provider checkout steps (Stripe/PayPal) require a human in a browser.
# - This script prints the provider redirect URL, then polls until the payment becomes SUCCESS,
#   then attempts a refund and validates refund status.
#
# Usage:
#   API_HOST=localhost API_PORT=8080 ./scripts/e2e-payment-refund.sh stripe
#   API_HOST=localhost API_PORT=8080 ./scripts/e2e-payment-refund.sh paypal

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_HOST="${API_HOST:-localhost}"
API_PORT="${API_PORT:-8080}"
BASE_URL="http://${API_HOST}:${API_PORT}"
CHANNEL="${1:-stripe}"

if ! command -v curl >/dev/null; then
  echo "[ERROR] curl is required"
  exit 1
fi
if ! command -v python3 >/dev/null; then
  echo "[ERROR] python3 is required"
  exit 1
fi

json_get() {
  python3 - <<'PY' "$1" "$2"
import json, sys
payload = json.loads(sys.argv[1])
key = sys.argv[2]
cur = payload
for part in key.split("."):
  if isinstance(cur, dict) and part in cur:
    cur = cur[part]
  else:
    cur = None
    break
print("" if cur is None else cur)
PY
}

register_api_user() {
  local suffix ts body
  ts="$(date +%s)"
  suffix="e2e-${ts}"
  body="$(curl -fsS -X POST "${BASE_URL}/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${suffix}\",\"password\":\"TestPass123!\"}")"
  echo "$body"
}

create_order() {
  local api_key="$1"
  curl -fsS -X POST "${BASE_URL}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${api_key}" \
    -d "{\"userId\":1,\"amount\":10.00,\"currency\":\"USD\",\"description\":\"e2e payment order\"}"
}

initiate_payment() {
  local api_key="$1"
  local order_id="$2"
  local payment_channel="$3"

  # Stripe requires explicit URLs in this codebase.
  local return_url="https://fusionx.fun/payment/success?orderId=${order_id}"
  local cancel_url="https://fusionx.fun/payment/cancel?orderId=${order_id}"

  curl -fsS -X POST "${BASE_URL}/api/v1/payment/request" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${api_key}" \
    -d "{
      \"orderId\": \"${order_id}\",
      \"amount\": 10.00,
      \"currency\": \"USD\",
      \"paymentChannel\": \"${payment_channel}\",
      \"description\": \"e2e payment request\",
      \"returnUrl\": \"${return_url}\",
      \"cancelUrl\": \"${cancel_url}\"
    }"
}

poll_payment_status() {
  local api_key="$1"
  local order_id="$2"
  local deadline_s="${3:-180}"
  local start now status body
  start="$(date +%s)"

  while true; do
    body="$(curl -fsS "${BASE_URL}/api/v1/payment/order/${order_id}" -H "X-API-Key: ${api_key}")"
    status="$(json_get "$body" "status")"
    if [[ "$status" == "SUCCESS" ]]; then
      echo "$body"
      return 0
    fi
    if [[ "$status" == "FAILED" ]]; then
      echo "$body"
      return 1
    fi
    now="$(date +%s)"
    if (( now - start > deadline_s )); then
      echo "$body"
      return 2
    fi
    sleep 3
  done
}

confirm_payment() {
  local api_key="$1"
  local order_id="$2"
  local payment_channel="$3"
  curl -fsS -X POST "${BASE_URL}/api/v1/payment/confirm" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${api_key}" \
    -d "{\"orderId\":\"${order_id}\",\"paymentChannel\":\"${payment_channel}\"}"
}

refund_payment() {
  local api_key="$1"
  local transaction_id="$2"
  curl -fsS -X POST "${BASE_URL}/api/v1/payment/refund" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${api_key}" \
    -d "{\"transactionId\":\"${transaction_id}\",\"reason\":\"e2e refund\"}"
}

echo "[INFO] base_url=${BASE_URL}"

user_json="$(register_api_user)"
api_key="$(json_get "$user_json" "apiKey")"
if [[ -z "$api_key" ]]; then
  echo "[ERROR] failed to register api user: $user_json"
  exit 1
fi
echo "[INFO] apiKey acquired (${api_key:0:8}...)"

order_json="$(create_order "$api_key")"
order_id="$(json_get "$order_json" "orderId")"
if [[ -z "$order_id" ]]; then
  order_id="$(json_get "$order_json" "id")"
fi
if [[ -z "$order_id" ]]; then
  echo "[ERROR] failed to create order: $order_json"
  exit 1
fi
echo "[INFO] orderId=${order_id}"

case "$CHANNEL" in
  stripe) payment_channel="STRIPE" ;;
  paypal) payment_channel="PAYPAL" ;;
  *)
    echo "[ERROR] unknown channel: $CHANNEL (expected stripe|paypal)"
    exit 1
    ;;
esac

payment_json="$(initiate_payment "$api_key" "$order_id" "$payment_channel")"
transaction_id="$(json_get "$payment_json" "transactionId")"
redirect_url="$(json_get "$payment_json" "redirectUrl")"
status="$(json_get "$payment_json" "status")"

echo "[INFO] payment status=${status} transactionId=${transaction_id}"
echo "[ACTION] Open this URL in a browser to complete checkout:"
echo "$redirect_url"

echo "[ACTION] After you complete checkout, press Enter to confirm..."
read -r _

echo "[INFO] confirming payment by querying provider API (webhook fallback)..."
confirm_json="$(confirm_payment "$api_key" "$order_id" "$payment_channel")" || true
confirm_status="$(json_get "$confirm_json" "status")"
confirm_provider_tx_id="$(json_get "$confirm_json" "providerTransactionId")"
confirm_error="$(json_get "$confirm_json" "errorMessage")"
echo "[INFO] confirm status=${confirm_status} providerTransactionId=${confirm_provider_tx_id}"
if [[ -n "$confirm_error" ]]; then
  echo "[WARN] confirm message: ${confirm_error}"
fi

echo "[INFO] polling payment status..."
polled="$(poll_payment_status "$api_key" "$order_id" 120)" || true
polled_status="$(json_get "$polled" "status")"
provider_tx_id="$(json_get "$polled" "providerTransactionId")"
echo "[INFO] payment polled status=${polled_status} providerTransactionId=${provider_tx_id}"
if [[ "$polled_status" != "SUCCESS" ]]; then
  echo "[ERROR] payment did not reach SUCCESS (try re-run confirm or check provider status)"
  echo "$polled"
  exit 1
fi

echo "[INFO] initiating refund..."
refund_json="$(refund_payment "$api_key" "$transaction_id")"
refund_status="$(json_get "$refund_json" "status")"
refund_error="$(json_get "$refund_json" "errorMessage")"

echo "[INFO] refund status=${refund_status}"
if [[ "$refund_status" != "COMPLETED" ]]; then
  echo "[ERROR] refund failed: ${refund_error}"
  echo "$refund_json"
  exit 1
fi

echo "[PASS] refund completed"
