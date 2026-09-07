# SSO role matrix with Newman

This test runs the attached collection flow once for each role: `manager`, `admin`, and `user`.
Every iteration obtains a fresh `accessToken`, stores it as `bearer_token`, and calls these APIs:

- `GET /api/me`
- `GET /api/admin/users`
- `GET /api/dashboard`
- `GET /api/reports`

## Run

Start the token issuer on port `9090` and the API on port `8080`, then run:

```sh
cd newman-rbac
npm install
npm test
```

The test prints the complete Markdown report and creates:

- `results/rbac-results.md` — each role's `/api/me` response followed by the consolidated table
- `results/newman-run.json` — Newman's detailed machine-readable report

To use different hosts:

```sh
TOKEN_BASE_URL=https://issuer.example.test \
API_BASE_URL=https://api.example.test \
npm test
```

## Expected statuses

`roles.json` contains optional expected status values. The supplied manager example is configured as
`403 / 200 / 403`. The admin and user expectations are intentionally `null`, so their actual statuses
are captured without guessing the intended access policy. Replace those `null` values with status codes
when the expected policy is confirmed; Newman will then assert them too.
