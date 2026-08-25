# Contributing

Thank you for improving the reference. Keep changes focused and do not commit credentials, real
tenant IDs, bearer tokens, user data, generated build output, or local environment files.

## Local checks

```bash
cd backend
./mvnw spotless:apply
./mvnw verify

cd ../frontend
npm ci
npm run format
npm run check
```

Add or update authorization tests whenever an endpoint, role, scope, audience, or security rule
changes. Update the Entra guide and flow examples when configuration or user-visible behavior
changes.

The backend is OpenAPI-first. Define HTTP operations and response schemas in
`backend/src/main/openapi/entra-sso-api.yaml`, regenerate with Maven, and implement generated
delegate interfaces in `src/main/java`. Never edit or commit `target/generated-sources/openapi`.

## Pull requests

- Explain the problem and the chosen behavior.
- Keep JWT/CORS mechanics in the security package and endpoint authorization on application
  delegates.
- Keep generated HTTP mappings and response models aligned with the OpenAPI contract.
- Preserve tenant-specific issuer and strict audience validation.
- Include tests for anonymous, insufficient-permission, and successful requests.
- Keep dependencies pinned through Maven dependency management and `package-lock.json`.
- Route browser diagnostics through `frontend/src/logger.ts`; never log tokens or secrets.
- Preserve or safely generate `X-Correlation-Id` in every HTTP path, including errors.

By contributing, you agree that your contribution is licensed under Apache License 2.0.
