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
#
# For demo environments without public ingress for Stripe webhooks:
# - After completing Stripe checkout, this script will replay a signed webhook event locally
#   via `scripts/stripe-webhook-replay.sh` to drive the full callback chain.

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
  local want_status="${4:-SUCCESS}"
  local start now status body
  start="$(date +%s)"

  while true; do
    body="$(curl -fsS "${BASE_URL}/api/v1/payment/order/${order_id}" -H "X-API-Key: ${api_key}")"
    status="$(json_get "$body" "status")"
    if [[ "$status" == "$want_status" ]]; then
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
provider_reference_id="$(json_get "$payment_json" "providerTransactionId")"

echo "[INFO] payment status=${status} transactionId=${transaction_id}"
echo "[ACTION] Open this URL in a browser to complete checkout:"
echo "$redirect_url"

if [[ "$CHANNEL" == "stripe" ]]; then
  echo "[INFO] stripe sessionId=${provider_reference_id}"
  echo "[ACTION] Complete Stripe checkout, then press Enter to replay the webhook locally..."
  read -r _

  # Best-effort: source env from known always-on path if present.
  env_file="/home/denji/.fusionxpay/.env.always-on"
  if [[ -f "$env_file" ]]; then
    ENV_FILE="$env_file" ORDER_ID="$order_id" SESSION_ID="$provider_reference_id" API_HOST="$API_HOST" API_PORT="$API_PORT" \
      "${ROOT_DIR}/scripts/stripe-webhook-replay.sh"
  else
    ORDER_ID="$order_id" SESSION_ID="$provider_reference_id" API_HOST="$API_HOST" API_PORT="$API_PORT" \
      "${ROOT_DIR}/scripts/stripe-webhook-replay.sh"
  fi
fi

if [[ "$CHANNEL" == "paypal" ]]; then
  echo "[INFO] paypal orderId=${provider_reference_id}"
  echo "[ACTION] Complete PayPal approval, then press Enter to trigger the return callback locally..."
  read -r _
  # Simulate PayPal redirect back to returnUrl for environments without public ingress.
  curl -fsS -o /dev/null "${BASE_URL}/api/v1/payment/paypal/return?token=${provider_reference_id}&orderId=${order_id}" || true
fi

echo "[INFO] waiting for payment SUCCESS via webhook/callback..."
polled="$(poll_payment_status "$api_key" "$order_id" 300 "SUCCESS")" || true
polled_status="$(json_get "$polled" "status")"
provider_tx_id="$(json_get "$polled" "providerTransactionId")"

echo "[INFO] payment polled status=${polled_status} providerTransactionId=${provider_tx_id}"

if [[ "$polled_status" != "SUCCESS" ]]; then
  echo "[ERROR] payment did not reach SUCCESS"
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

echo "[INFO] waiting for refund webhook to mark payment REFUNDED..."
post_refund="$(poll_payment_status "$api_key" "$order_id" 300 "REFUNDED")" || true
post_refund_status="$(json_get "$post_refund" "status")"
echo "[INFO] post-refund payment status=${post_refund_status}"
if [[ "$post_refund_status" != "REFUNDED" ]]; then
  echo "[ERROR] payment did not reach REFUNDED via webhook"
  echo "$post_refund"
  exit 1
fi

echo "[PASS] payment+refund completed (REFUNDED)"
