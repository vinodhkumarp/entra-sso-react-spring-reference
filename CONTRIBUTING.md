# Contributing

Thank you for improving the reference. Keep changes focused and do not commit credentials, real
tenant IDs, bearer tokens, user data, generated build output, or local environment files.

## Local checks

```bash
cd backend
./mvnw verify

cd ../frontend
npm ci
npm run build
```

Add or update authorization tests whenever an endpoint, role, scope, audience, or security rule
changes. Update the Entra guide and flow examples when configuration or user-visible behavior
changes.

## Pull requests

- Explain the problem and the chosen behavior.
- Keep JWT/CORS mechanics in the security package and endpoint authorization near each controller.
- Preserve tenant-specific issuer and strict audience validation.
- Include tests for anonymous, insufficient-permission, and successful requests.
- Keep dependencies pinned through Maven dependency management and `package-lock.json`.

By contributing, you agree that your contribution is licensed under Apache License 2.0.
