# Local Observability and Backup Runbook

## Scope

This runbook covers:

1. Local monitoring stack (Prometheus + Grafana).
2. Log inspection for always-on services.
3. MySQL backup and restore operations.

## Files

1. Monitoring compose: `docker-compose.monitoring.yml`
2. Prometheus config: `monitoring/prometheus/prometheus.yml`
3. Grafana provisioning:
   - `monitoring/grafana/provisioning/datasources/prometheus.yml`
   - `monitoring/grafana/provisioning/dashboards/default.yml`
4. Default dashboard:
   - `monitoring/grafana/dashboards/fusionxpay-overview.json`
5. Backup script: `scripts/backup-mysql.sh`
6. Restore script: `scripts/restore-mysql.sh`

## 1) Start Monitoring

```bash
docker compose \
  --env-file .env.always-on \
  -f docker-compose.always-on.yml \
  -f docker-compose.monitoring.yml \
  up -d prometheus grafana
```

### Verify

```bash
curl -fsS http://localhost:${PROMETHEUS_PORT:-9090}/-/healthy
curl -fsS http://localhost:${PROMETHEUS_PORT:-9090}/api/v1/targets
curl -fsS http://localhost:${GRAFANA_PORT:-3001}/api/health
```

Expected:

1. Prometheus health endpoint returns `Prometheus is Healthy`.
2. Targets include all five jobs and state `UP`.
3. Grafana health endpoint returns `"database":"ok"`.

## 2) Logs and Quick Triage

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

## 3) Database Backup

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

## 4) Suggested Weekly Routine

1. Verify gateway health:
   - `curl -fsS http://localhost:8080/actuator/health`
2. Verify monitoring health:
   - Prometheus and Grafana endpoints.
3. Run one manual backup and verify file generation.
4. Verify disk usage for backup directory.
