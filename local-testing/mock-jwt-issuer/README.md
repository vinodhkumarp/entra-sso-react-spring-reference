# Local mock JWT issuer

This loopback-only Spring Boot utility creates an ephemeral 3072-bit RSA key at startup, publishes
its public key as JWKS, and issues five-minute RS256 access tokens for three fixed test identities.
It is deliberately separate from the production backend and React application.

Run it from the repository root:

```bash
./backend/mvnw -f local-testing/mock-jwt-issuer/pom.xml spring-boot:run
```

Endpoints:

- `GET /.well-known/openid-configuration`
- `GET /oauth2/jwks`
- `GET /test-users`
- `POST /test-token` with `{"userId":"manager"}`

The private key never leaves process memory. Restarting the utility rotates the key and invalidates
previously issued tokens. This service has no authentication and must never be deployed or bound to
a non-loopback interface.
