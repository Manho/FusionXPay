# Auto Deploy From `main` To Local Ubuntu

## Overview

This workflow deploys backend services to a local Ubuntu host through a self-hosted GitHub Actions runner.

- Workflow file: `.github/workflows/deploy-local-main.yml`
- Trigger:
  - `pull_request` to `main` (validation only)
  - successful completion of `Docker Build & Push` on `main` (real deployment)
  - `workflow_dispatch` (manual trigger)
- Target profile:
  - `docker-compose.always-on.yml`
  - `~/.fusionxpay/.env.always-on`

## Prerequisites

1. A self-hosted runner is registered on the target Ubuntu host with labels `self-hosted` and `linux`.
2. Docker and Docker Compose are installed on the runner host.
3. Runner host contains `~/.fusionxpay/.env.always-on` with real values.
4. Runner user can execute Docker commands.
5. Runner host has outbound access to `ghcr.io`.

## Deployment Flow

1. `Docker Build & Push` publishes service images tagged with the commit SHA.
2. `Deploy Local (Main)` starts only after that workflow succeeds on `main`.
3. The deploy job checks out the exact `head_sha` that produced the published images.
4. The runner logs in to GHCR with the workflow `GITHUB_TOKEN`.
5. `scripts/deploy-local-main.sh`:
   - runs `scripts/deploy-always-on.sh`
   - pulls commit-pinned service images from GHCR
   - recreates services with the always-on compose profile
6. `scripts/check-always-on-health.sh` performs smoke checks.
7. If successful, `scripts/mark-successful-sha.sh` records the revision.
8. If any step fails, `scripts/rollback-local-main.sh`:
   - reads `~/.fusionxpay-deploy/last_successful_sha`
   - checks out that revision in a temporary git worktree
   - redeploys the previously published image tag for that revision

## Operational Commands

```bash
# Validate scripts locally
bash -n scripts/deploy-local-main.sh
bash -n scripts/mark-successful-sha.sh
bash -n scripts/rollback-local-main.sh

# Validate always-on compose profile
docker compose --env-file .env.always-on.example -f docker-compose.always-on.yml config >/dev/null

# Verify that the Docker image workflow can build service images
gh workflow run "Docker Build & Push"
```

## Failure Handling

1. If image publication fails, deployment never starts.
2. If deployment fails and no previous successful SHA exists, rollback is skipped with warning.
3. If deployment fails and a previous successful SHA exists, rollback redeploys that revision and re-checks gateway health.
4. The job remains failed after rollback, so failure is visible in Actions history.

## Required Secrets / Config

No custom GitHub secret is required by default for this workflow.
The workflow uses the built-in `GITHUB_TOKEN` with `packages: read` permission to pull GHCR images.
All runtime secrets are still read from `.env.always-on` on the self-hosted runner host.
