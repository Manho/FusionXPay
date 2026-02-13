# Local Observability and Backup Runbook

## Scope

This runbook covers:

1. Local monitoring stack (Prometheus + Grafana + Loki + Promtail).
2. Alert rule verification in Prometheus.
3. Log inspection for always-on services.
4. MySQL backup and restore operations.

## Files

1. Monitoring compose: `docker-compose.monitoring.yml`
2. Prometheus config: `monitoring/prometheus/prometheus.yml`
3. Prometheus alerts: `monitoring/prometheus/alerts.yml`
4. Loki config: `monitoring/loki/loki-config.yml`
5. Promtail config: `monitoring/promtail/promtail-config.yml`
6. Grafana provisioning:
   - `monitoring/grafana/provisioning/datasources/prometheus.yml`
   - `monitoring/grafana/provisioning/dashboards/default.yml`
7. Dashboards:
   - `monitoring/grafana/dashboards/fusionxpay-overview.json`
   - `monitoring/grafana/dashboards/jvm-details.json`
   - `monitoring/grafana/dashboards/http-api-details.json`
   - `monitoring/grafana/dashboards/infrastructure.json`
   - `monitoring/grafana/dashboards/logs.json`
8. Backup script: `scripts/backup-mysql.sh`
9. Restore script: `scripts/restore-mysql.sh`

## 1) Start Monitoring Stack

```bash
docker compose \
  --env-file .env.always-on \
  -f docker-compose.always-on.yml \
  -f docker-compose.monitoring.yml \
  up -d prometheus loki promtail grafana
```

## 2) Verify Monitoring Components

```bash
curl -fsS http://localhost:${PROMETHEUS_PORT:-9090}/-/healthy
curl -fsS http://localhost:${PROMETHEUS_PORT:-9090}/api/v1/targets
curl -fsS http://localhost:${PROMETHEUS_PORT:-9090}/api/v1/rules
curl -fsS http://localhost:${LOKI_PORT:-3100}/ready
curl -fsS http://localhost:${GRAFANA_PORT:-3001}/api/health
```

Expected:

1. Prometheus health endpoint returns `Prometheus is Healthy`.
2. Targets include all five jobs and state `UP`.
3. Rules API includes `ServiceDown`, `HighErrorRate`, `HighLatency`, `JVMHeapHigh`, `HikariPoolExhaustion`.
4. Loki readiness endpoint returns `ready`.
5. Grafana health endpoint returns `"database":"ok"`.

## 3) Verify Dashboards

Open Grafana at `http://localhost:${GRAFANA_PORT:-3001}` and verify these dashboards exist:

1. `FusionXPay Overview`
2. `FusionXPay JVM Details`
3. `FusionXPay HTTP API Details`
4. `FusionXPay Infrastructure`
5. `FusionXPay Logs`

## 4) Verify Centralized Logs

In Grafana Explore with Loki datasource, run:

```logql
{container=~"fusionxpay-.*"}
```

Then filter by level:

```logql
{container=~"fusionxpay-.*"} |= "ERROR"
```

If no logs appear:

```bash
docker logs --tail 100 fusionxpay-promtail
docker logs --tail 100 fusionxpay-loki
```

## 5) Logs and Quick Triage

Use container logs directly for local-first troubleshooting:

```bash
docker logs --tail 200 fusionxpay-api-gateway
docker logs --tail 200 fusionxpay-payment-service
docker logs --tail 200 fusionxpay-order-service
docker logs --tail 200 fusionxpay-notification-service
docker logs --tail 200 fusionxpay-admin-service
```

For continuous tracking:

```bash
docker compose --env-file .env.always-on -f docker-compose.always-on.yml logs -f
```

## 6) Database Backup

### Create Backup

```bash
./scripts/backup-mysql.sh ./.env.always-on
```

Behavior:

1. Dumps `${DB_NAME}` into `${BACKUP_DIR}` as compressed SQL.
2. Keeps latest `${BACKUP_RETENTION_COUNT}` backups.
3. Maintains `${DB_NAME}_latest.sql.gz` symlink.

### Restore Backup

```bash
RESTORE_CONFIRM=YES ./scripts/restore-mysql.sh ./backups/mysql/<your-backup>.sql.gz ./.env.always-on
```

Safety:

1. Restore is blocked unless `RESTORE_CONFIRM=YES`.
2. Script exits on missing env vars or missing backup file.

## 7) Suggested Weekly Routine

1. Verify API gateway health:
   - `curl -fsS http://localhost:8080/actuator/health`
2. Verify monitoring health:
   - Prometheus/Loki/Grafana endpoints.
3. Verify Prometheus alert rules are loaded.
4. Verify Loki query can find FusionXPay container logs.
5. Run one manual backup and verify file generation.
6. Verify disk usage for Prometheus/Loki and backup directories.
