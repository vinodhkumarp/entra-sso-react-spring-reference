# Microsoft Entra setup: beginner walkthrough

This guide configures a Microsoft Entra workforce tenant, one React single-page application (SPA),
one protected Spring Boot API, three application roles, and test users.

Portal labels occasionally change. Start at the
[Microsoft Entra admin center](https://entra.microsoft.com/) and use the search box if a menu has
moved.

## 1. Understand the objects you will create

| Entra object | Purpose |
|---|---|
| Tenant | Your organisation's isolated identity directory |
| User | A member or invited guest who signs in |
| API app registration | Describes the protected resource, scope, roles, and token audience |
| API enterprise application | The tenant-local service principal where users receive app roles |
| SPA app registration | Identifies the public React client and its redirect URI |
| Delegated permission | Allows React to call the API on behalf of the signed-in user |

An **application (client) ID** identifies an app. A **directory (tenant) ID** identifies the
directory. They are different values.

## 2. Create or select a workforce tenant

1. Sign in to the Entra admin center.
2. Check the tenant name shown in the top-right directory switcher.
3. If you do not control a workforce tenant, create one from **Identity → Overview → Manage
   tenants → Create**. Select a Microsoft Entra ID workforce tenant when offered a tenant type.
4. Switch into that tenant.
5. Open **Identity → Overview** and copy the **Tenant ID**.

A personal Microsoft account such as Outlook.com is not itself a workforce tenant. It can be
invited into one as a guest, but app registrations and role assignments belong to a tenant.

## 3. Create test users

If you are permitted to create member users:

1. Open **Identity → Users → All users → New user → Create new user**.
2. Enter a display name and user principal name, for example
   `sso-user@yourtenant.onmicrosoft.com`.
3. Set or auto-generate the initial password.
4. Create the user and safely record the temporary password.
5. Repeat if you want separate manager and administrator users.

If you want to test with a personal Microsoft account:

1. Select **New user → Invite external user**.
2. Enter the personal email address and send the invitation.
3. Accept the invitation while signed in to that same account.
4. Use this guest object for the later app-role assignment.

Do not assign `APP_USER` on the user-creation screen. Application roles exist only after the API
app is created, and they are assigned on the API's **Enterprise application**.

## 4. Register the protected API

1. Open **Identity → Applications → App registrations → New registration**.
2. Name it `entra-sso-reference-api`.
3. Select **Accounts in this organizational directory only** for the simplest tenant-specific
   design.
4. Leave the redirect URI empty and select **Register**.
5. From **Overview**, copy the **Application (client) ID**. This is `ENTRA_API_CLIENT_ID`.
6. Confirm the **Directory (tenant) ID** matches the tenant recorded earlier.

### Expose the delegated scope

1. In the API app registration, open **Expose an API**.
2. Select **Add** beside **Application ID URI** and accept `api://<API_CLIENT_ID>`.
3. Select **Add a scope**.
4. Enter:

   | Field | Value |
   |---|---|
   | Scope name | `access_as_user` |
   | Who can consent? | Admins and users, subject to your tenant policy |
   | Admin consent display name | Access the SSO reference API |
   | Admin consent description | Allow the application to access the SSO reference API for the signed-in user. |
   | User consent display name | Access the SSO reference API |
   | User consent description | Allow this application to access the SSO reference API on your behalf. |
   | State | Enabled |

5. Save the scope. Its complete value is
   `api://<API_CLIENT_ID>/access_as_user`.

### Create the application roles

1. In the same API app registration, open **App roles → Create app role**.
2. Create each role below with **Allowed member types** set to **Users/Groups** and **Enable this
   app role** selected.

   | Display name | Value | Description |
   |---|---|---|
   | Application user | `APP_USER` | View the dashboard |
   | Manager | `APP_MANAGER` | View the dashboard and reports |
   | Application administrator | `APP_ADMIN` | Access all example functionality |

The **Value** is what appears in the access token's `roles` claim and must exactly match the Spring
`@PreAuthorize` rules.

## 5. Assign users to roles

1. Open **Identity → Applications → Enterprise applications**.
2. Search for and select `entra-sso-reference-api`. Do not select the SPA.
3. Open **Users and groups → Add user/group**.
4. Under **Users**, select `sso-user@yourtenant.onmicrosoft.com` or the invited guest.
5. Under **Select a role**, choose **Application user** (`APP_USER`).
6. Select **Assign**.

Repeat the assignment with other test users and roles. Depending on tenant licensing, assigning a
group instead of individual users can require additional licensing.

Role changes do not alter an access token that has already been issued. Sign out and sign in again
after changing assignments.

## 6. Register the React SPA

1. Return to **App registrations → New registration**.
2. Name it `entra-sso-reference-spa`.
3. Select **Accounts in this organizational directory only**.
4. Under **Redirect URI**, select **Single-page application (SPA)** and enter
   `http://localhost:5173`.
5. Select **Register**.
6. Copy its **Application (client) ID**. This is `VITE_ENTRA_SPA_CLIENT_ID`.
7. Under **Authentication**, confirm the SPA redirect URI and add
   `http://localhost:5173` as the front-channel logout URL if that option is available.

Do not create a client secret. A browser is a public client and cannot protect one. MSAL uses the
authorization-code flow with Proof Key for Code Exchange (PKCE).

## 7. Give the SPA delegated API permission

1. In the SPA registration, open **API permissions → Add a permission**.
2. Select **My APIs**, then `entra-sso-reference-api`.
3. Select **Delegated permissions**.
4. Select `access_as_user`, then **Add permissions**.
5. If your tenant requires administrator consent, select **Grant admin consent** or ask a tenant
   administrator to do so.

The SPA requests the scope at runtime; the permission entry declares that this client may request
it.

## 8. Configure the local applications

Set the backend environment:

```bash
export ENTRA_TENANT_ID='<directory-tenant-id>'
export ENTRA_API_CLIENT_ID='<api-application-client-id>'
export UI_ORIGIN='http://localhost:5173'
```

Copy `frontend/.env.example` to `frontend/.env.local` and enter:

```dotenv
VITE_ENTRA_TENANT_ID=<directory-tenant-id>
VITE_ENTRA_SPA_CLIENT_ID=<spa-application-client-id>
VITE_ENTRA_API_CLIENT_ID=<api-application-client-id>
VITE_API_BASE_URL=http://localhost:8080
```

All three ID values are identifiers, not secrets. Still use environment-specific configuration so
the repository contains no real tenant details.

## 9. Run the end-to-end test

Start the backend and UI as described in the root README, then:

1. Open `http://localhost:5173` in a private browser window.
2. Select **Sign in with Microsoft**.
3. If prompted for consent, verify that the requested app and permission are expected.
4. Sign in as the `APP_USER` test user.
5. Confirm the user card contains `APP_USER` and `DASHBOARD_VIEW`.
6. Call the dashboard and expect `200`.
7. Try Reports or Admin and expect those controls to be hidden; a direct request would return
   `403`.
8. Assign `APP_MANAGER`, sign out/in, and verify Dashboard and Reports succeed.
9. Assign `APP_ADMIN`, sign out/in, and verify `/api/admin/users` succeeds.

## 10. Inspect a token safely

For local diagnosis, use the browser network panel to identify a failing API request and inspect
only the non-sensitive claim names you need. Do not paste a real bearer token into tickets, logs,
chat, source control, or a public website. A JWT access token is a credential until it expires.

Expected claims include:

```json
{
  "aud": "<API_CLIENT_ID>",
  "iss": "https://login.microsoftonline.com/<TENANT_ID>/v2.0",
  "scp": "access_as_user",
  "roles": ["APP_USER"],
  "tid": "<TENANT_ID>",
  "oid": "<USER_OBJECT_ID>"
}
```

The API—not React—verifies the signature, issuer, audience, and time claims.
