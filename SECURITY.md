# Security policy

## Supported versions

Until a stable release is published, security fixes are applied to the latest commit on `main`.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability and do not include access tokens, tenant
identifiers, personal information, or credentials in a report.

After publishing this repository, enable GitHub **Private vulnerability reporting** and use that
channel. Until it is enabled, repository adopters should use their organisation's approved private
security-reporting channel. The maintainer should acknowledge a report, assess impact, coordinate a
fix, and publish an advisory without exposing reporter or user data.

## Important scope note

This repository is a reference implementation, not a hosted identity service. Deployers are
responsible for tenant configuration, threat modelling, patching, ingress controls, monitoring,
incident response, privacy requirements, and testing their domain authorization rules.

Everything under `local-testing/` is intentionally unauthenticated test tooling. It binds to
loopback by default and must never be deployed, exposed through an ingress, or used to establish a
real user identity.
