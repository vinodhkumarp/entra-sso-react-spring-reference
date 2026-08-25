# Architecture and security design

## Responsibilities

| Component | Responsibility |
|---|---|
| React + MSAL | Start login, obtain an API access token, attach it to API requests, render UI hints |
| Microsoft Entra ID | Authenticate the user, apply tenant policies, issue signed tokens with scopes and roles |
| Spring Security | Validate the token and enforce the required delegated scope |
| Backend controllers | Enforce endpoint-specific app roles and return application data |

React is not an authorization boundary. Users can alter browser state, so every sensitive backend
method must enforce its own role requirement.

## End-to-end sequence

```mermaid
sequenceDiagram
    actor User
    participant UI as React + MSAL
    participant Entra as Microsoft Entra ID
    participant API as Spring Boot backend

    User->>UI: Select Sign in
    UI->>Entra: Authorization request + PKCE challenge
    Entra->>User: Authenticate and apply tenant policies
    Entra-->>UI: Authorization code
    UI->>Entra: Code + PKCE verifier
    Entra-->>UI: Access token for API audience
    UI->>API: GET /api/me + Bearer token
    API->>API: Validate signature, iss, aud, exp, nbf
    API->>API: Require access_as_user and map roles
    API-->>UI: Roles + functionality
    UI->>API: GET protected endpoint + Bearer token
    API->>API: Repeat validation + @PreAuthorize
    API-->>UI: 200, 401, or 403
```

## Why there is no authentication controller

An endpoint accepting usernames and passwords would expand credential-handling risk and bypass
Entra capabilities such as MFA and Conditional Access. The SPA is a public OAuth client and uses
authorization code with PKCE. Neither React nor Spring needs a client secret for this delegated
user flow.

## JWT validation location

The Spring Security bearer-token filter extracts the `Authorization` header. Boot configures a
`JwtDecoder` from the tenant-specific issuer and expected audience. Before any controller runs, it
validates:

- Cryptographic signature and signing-key ID
- `iss` against the configured tenant issuer
- `aud` against the API application/client ID
- `exp` and `nbf` timestamps

After validation, the custom converter maps `scp` values to `SCOPE_*` authorities and `roles` to
`ROLE_*` authorities. Claim conversion is not token validation; it occurs after validation.

## Authorization layers

```mermaid
flowchart TD
    Request["Bearer request"] --> JWT{"JWT valid?"}
    JWT -->|No| U401["401 Unauthorized"]
    JWT -->|Yes| Scope{"Has access_as_user?"}
    Scope -->|No| F403["403 Forbidden"]
    Scope -->|Yes| Role{"Has endpoint role?"}
    Role -->|No| F403
    Role -->|Yes| Controller["Controller executes"]
```

The scope means the SPA may call this API on behalf of the user. The app role decides which
business function that user may perform.

## `/api/me`

`/api/me` returns identity claims, roles, and configured UI functionality from the already
validated access token. It does not issue or return the bearer token. The UI uses this response to
hide irrelevant controls, but each controller still uses `@PreAuthorize`.

## CORS

CORS executes before bearer authentication so an unauthenticated browser preflight can succeed.
Only the configured React origin, expected methods, and `Authorization`/`Content-Type` headers are
allowed. CORS does not authenticate calls and does not affect non-browser clients.

## When to split the backend later

The single backend is the simplest design while the endpoints share ownership, deployment, and a
security boundary. If they later become independently governed services, give each resource API
its own Entra registration, audience, scope, deployment, and JWT validation. React would then
acquire the correct audience-specific token for each API.

