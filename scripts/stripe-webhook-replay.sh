#!/usr/bin/env bash
set -euo pipefail

# Replay a Stripe webhook event to the local gateway.
#
# This is intended for demo environments where Stripe cannot reach inbound webhook URLs.
# It queries Stripe for the PaymentIntent (pi_...) from a Checkout Session (cs_...),
# then sends a signed `payment_intent.succeeded` event to:
#   POST /api/v1/payment/webhook/stripe
#
# Usage:
#   ORDER_ID=<uuid> SESSION_ID=<cs_...> API_HOST=localhost API_PORT=8080 ./scripts/stripe-webhook-replay.sh
#
# Optional:
#   ENV_FILE=/path/to/.env.always-on

API_HOST="${API_HOST:-localhost}"
API_PORT="${API_PORT:-8080}"
BASE_URL="http://${API_HOST}:${API_PORT}"

ORDER_ID="${ORDER_ID:-}"
SESSION_ID="${SESSION_ID:-}"
ENV_FILE="${ENV_FILE:-}"

if [[ -z "$ORDER_ID" || -z "$SESSION_ID" ]]; then
  echo "[ERROR] Missing ORDER_ID or SESSION_ID"
  echo "Example: ORDER_ID=<uuid> SESSION_ID=<cs_...> ./scripts/stripe-webhook-replay.sh"
  exit 1
fi

if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

if [[ -z "${STRIPE_SECRET_KEY:-}" ]]; then
  echo "[ERROR] STRIPE_SECRET_KEY is required (set env or source ENV_FILE)"
  exit 1
fi
if [[ -z "${STRIPE_WEBHOOK_SECRET:-}" ]]; then
  echo "[ERROR] STRIPE_WEBHOOK_SECRET is required (set env or source ENV_FILE)"
  exit 1
fi

python3 - "$BASE_URL" "$ORDER_ID" "$SESSION_ID" <<'PY'
import hashlib
import hmac
import json
import os
import sys
import time
import uuid
import urllib.request

base_url = sys.argv[1].rstrip("/")
order_id = sys.argv[2]
session_id = sys.argv[3]

stripe_secret_key = os.environ["STRIPE_SECRET_KEY"]
stripe_webhook_secret = os.environ["STRIPE_WEBHOOK_SECRET"]

def http_json(method: str, url: str, headers=None, body: str | None = None):
    headers = dict(headers or {})
    data = None if body is None else body.encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        text = resp.read().decode("utf-8")
        return resp.status, text

stripe_url = f"https://api.stripe.com/v1/checkout/sessions/{session_id}"
status, session_text = http_json(
    "GET",
    stripe_url,
    headers={"Authorization": f"Bearer {stripe_secret_key}"},
)
if status != 200:
    raise SystemExit(f"[ERROR] Stripe session fetch failed: http={status} body={session_text[:200]}")

session = json.loads(session_text)
payment_intent_id = session.get("payment_intent")
if not payment_intent_id:
    raise SystemExit("[ERROR] Stripe session has no payment_intent yet (is checkout completed?)")

# stripe-java expects event.api_version to match Stripe.API_VERSION used by the library.
api_version = "2024-10-28.acacia"

payload_obj = {
    "id": "evt_demo_" + uuid.uuid4().hex,
    "object": "event",
    "api_version": api_version,
    "created": int(time.time()),
    "livemode": False,
    "pending_webhooks": 1,
    "type": "payment_intent.succeeded",
    "data": {
        "object": {
            "id": payment_intent_id,
            "object": "payment_intent",
            "status": "succeeded",
            "metadata": {
                "orderId": order_id,
            },
        }
    },
}

payload = json.dumps(payload_obj, separators=(",", ":"), ensure_ascii=True)
ts = int(time.time())
signed_payload = f"{ts}.{payload}".encode("utf-8")
sig = hmac.new(stripe_webhook_secret.encode("utf-8"), signed_payload, hashlib.sha256).hexdigest()
signature_header = f"t={ts},v1={sig}"

webhook_url = f"{base_url}/api/v1/payment/webhook/stripe"
status2, body2 = http_json(
    "POST",
    webhook_url,
    headers={
        "Content-Type": "application/json",
        "Stripe-Signature": signature_header,
    },
    body=payload,
)

if status2 != 200:
    raise SystemExit(f"[ERROR] webhook replay failed: http={status2} body={body2[:200]}")

print("[OK] webhook replayed")
PY

