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

## Registration Example

```bash
curl -X POST http://<connect-host>:8083/connectors \
  -H 'Content-Type: application/json' \
  -d @ops/kafka-connect/platform-audit-sink.json
```

## Notes

- API Gateway remains the only producer for `platform-audit-log`.
- CLI and MCP do not publish Kafka events directly; they only attach `X-Audit-*` metadata to gateway-bound requests.
- `platform_audit_log` must exist before the connector starts.
