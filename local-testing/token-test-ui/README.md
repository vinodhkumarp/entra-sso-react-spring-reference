# Local bearer-token test UI

This separate React application requests a short-lived token from `mock-jwt-issuer`, keeps it only
in memory, and sends it to the production backend as `Authorization: Bearer <token>` with a new
correlation ID. It does not import MSAL or modify the production React application.

```bash
cd local-testing/token-test-ui
npm ci
npm run dev
```

Open `http://localhost:5174`. Start the mock issuer and production backend first, following
`docs/local-jwt-testing.md`.
