# Phase 3 Acceptance Checklist (Local Always-On)

Use this checklist after all Phase 3 tasks are merged and deployed.

## Environment Baseline

- [ ] Backend running from `docker-compose.always-on.yml`
- [ ] Tunnel configured for `https://api.<your-domain>`
- [ ] Frontend deployed on Vercel and points to gateway domain

## Functional Checks

- [ ] Gateway health is `UP`
  - Evidence: `curl -fsS https://api.<your-domain>/actuator/health`
- [ ] Admin login succeeds from frontend
  - Evidence: login screenshot / browser verification
- [ ] Order list endpoint works through gateway
  - Evidence: frontend order list or API response
- [ ] Stripe webhook endpoint reachable
  - Evidence: `https://api.<your-domain>/api/v1/payment/webhook/stripe`
- [ ] PayPal webhook endpoint reachable
  - Evidence: `https://api.<your-domain>/api/v1/payment/paypal/webhook`

## Deployment and Rollback

- [ ] `main` push triggers deploy workflow
- [ ] Post-deploy smoke check passes
- [ ] Failure path tested with rollback to last successful SHA
- [ ] Rollback evidence stored (workflow run URL)

## Monitoring and Logs

- [ ] Prometheus targets for all five services are `UP`
- [ ] Grafana dashboard `FusionXPay Local Overview` has data
- [ ] Service logs are searchable with `docker logs`

## Backup and Restore

- [ ] MySQL backup generated with `scripts/backup-mysql.sh`
- [ ] Backup retention policy works as expected
- [ ] Restore drill executed with `scripts/restore-mysql.sh`
- [ ] Restore evidence recorded (time + backup file name)

## Resource Stability (8GB Host)

- [ ] Services run continuously without OOM restart
- [ ] Runtime memory remains around half of host memory in normal idle load
- [ ] Monitoring + app stack stay stable after host reboot
