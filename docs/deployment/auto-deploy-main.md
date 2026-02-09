# Auto Deploy From `main` To Local Ubuntu

## Overview

This workflow deploys backend services to a local Ubuntu host through a self-hosted GitHub Actions runner.

- Workflow file: `.github/workflows/deploy-local-main.yml`
- Trigger:
  - `pull_request` to `main` (validation only)
  - `push` to `main` (real deployment)
  - `workflow_dispatch` (manual trigger)
- Target profile:
  - `docker-compose.always-on.yml`
  - `.env.always-on`

## Prerequisites

1. A self-hosted runner is registered on the target Ubuntu host with labels `self-hosted` and `linux`.
2. Docker and Docker Compose are installed on the runner host.
3. Repository working directory contains `.env.always-on` with real values.
4. Runner user can execute Docker commands.

## Deployment Flow

1. Checkout source code.
2. Verify `.env.always-on` exists.
3. Execute `scripts/deploy-local-main.sh`:
   - runs `scripts/deploy-always-on.sh`
   - rebuilds and recreates services with the always-on compose profile
4. Execute `scripts/check-always-on-health.sh` for smoke checks.
5. If successful, execute `scripts/mark-successful-sha.sh`.
6. If any step fails, execute `scripts/rollback-local-main.sh`:
   - reads `~/.fusionxpay-deploy/last_successful_sha`
   - deploys previous successful revision by temporary git worktree

## Operational Commands

```bash
# Validate scripts locally
bash -n scripts/deploy-local-main.sh
bash -n scripts/mark-successful-sha.sh
bash -n scripts/rollback-local-main.sh

# Validate always-on compose profile
docker compose --env-file .env.always-on.example -f docker-compose.always-on.yml config >/dev/null
```

## Failure Handling

1. If deployment fails and no previous successful SHA exists, rollback is skipped with warning.
2. If deployment fails and a previous successful SHA exists, rollback redeploys that revision and re-checks gateway health.
3. The job remains failed after rollback, so failure is visible in Actions history.

## Required Secrets / Config

No GitHub secret is required by default for this workflow.
All runtime secrets are read from `.env.always-on` on the self-hosted runner host.
