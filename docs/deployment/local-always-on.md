# Local Always-On Deployment (8GB Host)

## Overview

This deployment profile is designed for a long-running demo environment on an 8GB Ubuntu host.

- Use a single public entrypoint through API Gateway.
- Reuse existing NAS middleware (Eureka, Kafka, DB, Redis) instead of running duplicate infra locally.
- Keep runtime stable with low memory limits (target service memory around 4GB in steady state).

## Files

- Compose profile: `docker-compose.always-on.yml`
- Env template: `.env.always-on.example`
- Deploy script: `scripts/deploy-always-on.sh`
- Health check script: `scripts/check-always-on-health.sh`

## 1. Prepare Environment

1. Copy `.env.always-on.example` to `.env.always-on`.
2. Fill real NAS endpoints and secrets.
3. Verify Docker and Docker Compose are installed.

## 2. Deploy

```bash
./scripts/deploy-always-on.sh ./.env.always-on
```

## 3. Verify Health

```bash
./scripts/check-always-on-health.sh ./.env.always-on
```

Expected:

- API Gateway health endpoint returns `UP`.
- All five application containers are running.

## 4. Observe Memory Footprint

```bash
docker stats --no-stream \
  fusionxpay-api-gateway \
  fusionxpay-payment-service \
  fusionxpay-order-service \
  fusionxpay-notification-service \
  fusionxpay-admin-service
```

Target:

- Stable long-running profile around 4GB service memory usage in normal idle traffic.

## 5. Stop Services

```bash
docker compose --env-file ./.env.always-on -f docker-compose.always-on.yml down
```

## Notes

- This profile intentionally keeps logging at INFO level for long-running low-resource operation.
- If any service needs temporary diagnostics, raise log level briefly and revert to INFO.
- Local monitoring and backup runbook:
  - `docs/operations/local-observability-backup.md`
