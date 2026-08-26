# Troubleshooting

## Account does not exist in tenant

Example:

```text
Selected user account does not exist in tenant ... and cannot access the application ...
```

The login request is targeting a tenant that does not contain that member or guest user.

1. Confirm `VITE_ENTRA_TENANT_ID` is the Directory (tenant) ID that owns both app registrations.
2. Switch to that directory in the Entra admin center.
3. Create the member user or invite the personal account as a guest.
4. Accept a guest invitation with the invited account.
5. Assign the user on the API **Enterprise application**.
6. Clear the local MSAL session or use a private window, then retry.

Avoid `common` as a quick fix for this tenant-specific sample. It weakens the intended issuer
boundary and conflicts with the backend's tenant-specific issuer validation.

## Vite says `Configuration` is not exported

Example:

```text
does not provide an export named 'Configuration'
```

`Configuration` is a TypeScript type, not a runtime JavaScript export. Import it with
`import type`:

```ts
import { LogLevel, PublicClientApplication } from "@azure/msal-browser";
import type { Configuration, RedirectRequest, SilentRequest } from "@azure/msal-browser";
```

This repository already uses type-only imports. If Vite still serves an old optimized module,
stop the dev server, remove Vite's generated cache under `node_modules/.vite`, and restart it.

## `401` with `The iss claim is not valid`

The token issuer and API-configured issuer differ. Common causes are:

- React used a different tenant ID.
- The API uses `common`, `organizations`, a v1 issuer, or a different tenant.
- An ID token was sent instead of the API access token.

The backend must use:

```text
https://login.microsoftonline.com/<same-tenant-id>/v2.0
```

React's authority must use that same tenant. The API validates the access token before any
controller runs.

## `401` with an invalid audience

The token was issued for another resource. Confirm:

- React requests `api://<API_CLIENT_ID>/access_as_user`.
- `ENTRA_API_CLIENT_ID` is the API registration's client ID, not the SPA client ID.
- The access token's `aud` matches the backend audience.

If you split the application into separately registered APIs later, React must acquire a separate
token for each audience.

## `403` even though login succeeded

Authentication succeeded, but authorization failed.

- If the token lacks `scp: access_as_user`, check the SPA's delegated API permission and requested
  scope.
- If the token lacks `roles`, assign the user on the API enterprise application.
- If the token contains an old role, sign out and sign in again after changing the assignment.
- Match role values exactly: `APP_USER`, `APP_MANAGER`, and `APP_ADMIN`.

## `/api/me` works but another endpoint returns `403`

This is expected when the user has the API scope but not the method's required app role.
`/api/me` reports roles and UI functionality; it does not grant authorization.

## Browser CORS failure

1. Confirm the UI actually runs at `http://localhost:5173`.
2. Confirm `UI_ORIGIN` contains that exact origin—scheme, host, and port.
3. Do not include a path or trailing slash in an origin.
4. Confirm the request uses an allowed method and only expected headers.

CORS and JWT validation solve different problems. A command-line HTTP client is not governed by
browser CORS and still requires a valid access token.

## Correlation ID is missing or different

- Use the response header as the effective ID; the backend returns it on both success and errors.
- Confirm the browser preflight allows and the response exposes `X-Correlation-Id`.
- The backend replaces values containing spaces, control characters, or more than 128 characters.
- Search the backend JSON logs for `"X-Correlation-Id":"<correlation-id>"`.
- A request that never reached the backend cannot have a backend log entry; inspect the frontend
  `api.request.network_failed` event instead.

Never paste an access token into logs or support tickets. The correlation ID is designed to locate
the request without sharing credentials.

If `traceparent` is empty, no valid W3C version-00 header reached the backend. This is normal until
browser or gateway tracing instrumentation is configured. The filter deliberately rejects
malformed and all-zero trace/parent identifiers.

## Redirect URI mismatch

The redirect passed by React must exactly match a URI registered under the SPA platform. Register
`http://localhost:5173` for local development. Do not register it under the Web platform, and do
not use wildcard production redirect URIs.

## `401` versus `403`

| Status | Meaning in this sample |
|---|---|
| `401 Unauthorized` | No usable identity: missing, expired, wrongly signed, wrong issuer, or wrong audience token |
| `403 Forbidden` | Valid identity but missing the required delegated scope or application role |

Use the API's `WWW-Authenticate` response header for diagnostics, but never enable logging of the
complete bearer token.

## Backend cannot discover signing keys

The API needs outbound HTTPS access to the configured tenant's OpenID Connect metadata and signing
keys. Check DNS, proxy, firewall, trust store, tenant ID, and system clock. Do not disable signature
validation or hard-code a copied signing key as a workaround; Microsoft rotates keys.

## `/api/me` is called continuously after login

Do not call `setActiveAccount` for every `ACQUIRE_TOKEN_SUCCESS` event. Loading `/api/me` first
acquires a token; updating the active account for that event changes React authentication state and
can retrigger the effect that loads `/api/me`, creating a request loop.

This repository changes the active account only after `LOGIN_SUCCESS` and makes the `/api/me`
effect depend on the stable `homeAccountId` rather than the `AccountInfo` object reference. React
Strict Mode can still produce two initial calls during local development, but calls must not
continue indefinitely.
