# Implemented flow examples

## 1. Interactive sign-in

`frontend/src/App.tsx` calls `instance.loginRedirect(loginRequest)`. MSAL generates PKCE material,
redirects to the tenant-specific Entra authority, and processes the returned authorization code in
`frontend/src/main.tsx` before React renders authenticated content.

Expected result: the browser returns to `http://localhost:5173` and MSAL has an active account.

## 2. Silent access-token acquisition

`frontend/src/api.ts` calls `acquireTokenSilent` immediately before an API request. MSAL returns a
cached valid token or renews it. Only an `InteractionRequiredAuthError` causes an interactive
redirect fallback.

The request contains:

```http
Authorization: Bearer <access-token>
```

The API never returns that token and React does not place it in application state or local storage.

## 3. `/api/me` and UI rendering

After sign-in, React calls `GET /api/me`. A response resembles:

```json
{
  "subjectId": "8db1...",
  "username": "user@tenant.onmicrosoft.com",
  "displayName": "Example User",
  "tenantId": "2a61...",
  "roles": ["APP_MANAGER"],
  "functionalities": ["DASHBOARD_VIEW", "REPORTS_VIEW"],
  "tokenExpiresAt": "2026-08-25T03:00:00Z"
}
```

React shows buttons represented by `functionalities`. This does not authorize an endpoint; the
backend checks every request independently.

## 4. Dashboard success

Representative validated token claims:

```json
{
  "scp": "access_as_user",
  "roles": ["APP_USER"]
}
```

Processing order:

1. The backend validates the JWT.
2. The filter chain requires `SCOPE_access_as_user`.
3. `BusinessController.dashboard()` requires `APP_USER`, `APP_MANAGER`, or `APP_ADMIN`.
4. The controller returns dashboard JSON.

## 5. Reports rejection and success

An `APP_USER` token passes scope validation but fails the method role check, producing `403
Forbidden`. `APP_MANAGER` or `APP_ADMIN` succeeds.

This demonstrates why a valid access token does not grant every operation.

## 6. Administrator flow

React calls `GET /api/admin/users` on the same backend. `AdminUserController.users()` requires
`ROLE_APP_ADMIN`. `APP_USER` and `APP_MANAGER` receive `403`; `APP_ADMIN` receives the example user
projection.

## 7. Missing or invalid token

- No `Authorization` header: `401 Unauthorized`
- Expired token: `401 Unauthorized`
- Wrong issuer: `401 Unauthorized`
- Wrong audience: `401 Unauthorized`
- Invalid signature: `401 Unauthorized`

The protected controller does not execute.

## 8. Valid token without permission

- Missing `access_as_user`: `403 Forbidden` at the filter chain
- Has the scope but lacks the endpoint role: `403 Forbidden` at `@PreAuthorize`

## 9. Logout

React calls `logoutRedirect` with the active account and returns to the application origin. This
clears the local MSAL account/cache for the session and asks Entra to end its session. The stateless
backend does not need a Spring logout endpoint.

## 10. Add another protected endpoint

1. Add a controller method under `backend/src/main/java/com/example/entrasso/api`.
2. Map it below `/api/` so the filter chain requires `access_as_user`.
3. Add the smallest suitable `@PreAuthorize` app-role rule.
4. If it appears in the UI, add a role-to-functionality entry in `application.yml`.
5. Add tests for no token, missing scope, wrong role, and success.
6. Add a typed React call through `callApi` if the UI consumes it.
