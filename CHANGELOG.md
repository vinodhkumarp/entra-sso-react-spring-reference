# Changelog

All notable changes to this project will be documented here. The format follows Keep a Changelog,
and releases should use semantic versioning.

## [Unreleased]

### Added

- React and MSAL authorization-code-with-PKCE example.
- Single Spring Boot Entra resource-server application.
- Dashboard, reports, and administrator endpoint examples.
- App-role authorization, delegated-scope enforcement, and `/api/me` UI metadata.
- Beginner Entra setup, architecture, flow, and troubleshooting guides.
- Automated backend and frontend build checks.
- Spotless/google-java-format and Prettier formatting checks.
- Central browser logger and backend correlation-ID request filter.
- End-to-end `X-Correlation-Id` propagation, generation, CORS exposure, MDC, and tests.
- Six-field JSON backend logging with validated W3C `traceparent` propagation and stack traces.
- OpenAPI 3.0 contract with build-time Spring Boot 4 controller and response-model generation.
- Delegate implementations that keep role authorization and business behavior outside generated
  code.
- JaCoCo gates that fail both Java builds below 85% aggregate handwritten line coverage.
- Project-owned Checkstyle rules, SpotBugs bytecode analysis, and Java/Maven environment
  enforcement.
- Opt-in OWASP Dependency-Check scans, npm audit commands, CycloneDX SBOM generation, and scheduled
  dependency-security automation.
- CI and Dependabot coverage for the production applications and isolated local-testing utilities.
- Root Makefile with guided setup, environment validation, ordered Entra/local startup, quality
  gates, formatting, vulnerability scans, SBOM generation, and cleanup commands.
