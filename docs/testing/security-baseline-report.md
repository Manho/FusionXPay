# Security Hardening Checklist (Template)

This document is a **public checklist** for security improvements and verification steps.
It intentionally avoids publishing environment-specific details or known-unfixed weaknesses.

## Goals

- Reduce accidental exposure (debug endpoints, overly permissive configs)
- Protect secrets and sensitive data at rest and in transit
- Establish repeatable security verification steps

## Checklist

### Access Control

- [ ] Restrict operational endpoints (health/metrics/admin tooling) to trusted networks or authenticated users.
- [ ] Ensure admin routes require a valid JWT and do not accept API keys as a substitute.
- [ ] Validate that protected routes reject missing/invalid credentials with `401`/`403` consistently.

### Secrets and Credentials

- [ ] Do not store secrets in git-tracked files.
- [ ] Store API keys in a non-reversible form (hash + salt) and compare using constant-time checks.
- [ ] Ensure JWT secrets are long, random, and rotated when leaked.

### Token Hygiene

- [ ] Set a reasonable JWT expiration and validate it on every request.
- [ ] Confirm refresh/rotation strategy if long-lived sessions are needed.
- [ ] Verify logout/token revocation behavior (if supported).

### Transport and Headers

- [ ] Enforce TLS at the edge (reverse proxy / ingress).
- [ ] Add standard security headers at the edge or app layer:
  - `X-Content-Type-Options`
  - `X-Frame-Options`
  - `Referrer-Policy`
  - `Content-Security-Policy` (if serving web content)
  - `Strict-Transport-Security` (only when TLS is enforced)

### Input Validation and Error Handling

- [ ] Validate request payloads and return structured error messages without stack traces.
- [ ] Sanitize error responses for admin endpoints to avoid leaking internal exceptions.
- [ ] Confirm parameterized SQL/JPA usage for all DB queries.

### Dependency and Supply Chain

- [ ] Run dependency vulnerability scanning in CI (e.g. OWASP dependency check / Snyk / GitHub alerts).
- [ ] Pin base images and update regularly.

### Observability and Auditability

- [ ] Log authentication failures and permission denials with minimal PII.
- [ ] Record security-relevant events (admin actions, refunds, configuration changes).
- [ ] Ensure logs do not include secrets/tokens.

## How To Verify (Examples)

- Review service configs for exposed operational endpoints and ensure access is restricted.
- Verify admin endpoints require JWT by running a small set of curl checks locally in a safe environment.
- Run dependency scan profile in CI (do not require it for every local build).

