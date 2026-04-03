# Platform Audit Sink

FusionXPay now emits platform-level ingress audit events from API Gateway to the `platform-audit-log` Kafka topic.

## Purpose

Use Kafka Connect JDBC Sink to persist gateway audit events into MySQL table `platform_audit_log`.

## Connector Template

The repository includes a ready-to-adapt connector template:

- [ops/kafka-connect/platform-audit-sink.json](/Users/manho/src/FusionXPay/ops/kafka-connect/platform-audit-sink.json)

Adjust the following fields for your environment before registering the connector:

- `connection.url`
- `connection.user`
- `connection.password`
- `topics`
- `table.name.format`

## Auto Deployment

The always-on deployment now provisions this infrastructure automatically:

- `scripts/deploy-always-on.sh` applies [07-platform-audit-log.sql](/Users/manho/src/FusionXPay/mysql-init/07-platform-audit-log.sql)
- `docker-compose.always-on.yml` starts a `kafka-connect` worker
- `scripts/ensure-platform-audit-infra.sh` registers or updates `platform-audit-log-mysql-sink`

The deployment host must expose a working `.env.always-on` with:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_CONNECT_URL` or `KAFKA_CONNECT_PORT`
- `FUSIONX_PLATFORM_AUDIT_TOPIC`

Cold starts for `cp-kafka-connect` can take a few minutes because the JDBC plugin is installed at container startup.
If your host or network is slower, tune these optional env vars in `.env.always-on`:

- `PLATFORM_AUDIT_CONNECT_MAX_RETRIES`
- `PLATFORM_AUDIT_CONNECT_RETRY_SECONDS`
- `PLATFORM_AUDIT_CONNECTOR_MAX_RETRIES`
- `PLATFORM_AUDIT_CONNECTOR_RETRY_SECONDS`
- `KAFKA_CONNECT_HEALTH_MAX_RETRIES`

## Manual Registration Example

```bash
curl -X POST http://<connect-host>:8083/connectors \
  -H 'Content-Type: application/json' \
  -d @ops/kafka-connect/platform-audit-sink.json
```

## Notes

- API Gateway remains the only producer for `platform-audit-log`.
- CLI and MCP do not publish Kafka events directly; they only attach `X-Audit-*` metadata to gateway-bound requests.
- `platform_audit_log` must exist before the connector starts.
