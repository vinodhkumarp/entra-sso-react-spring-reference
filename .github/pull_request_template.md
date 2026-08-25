## Summary

Describe the behavior and why it is needed.

## Security impact

Describe changes to authentication, scopes, roles, claims, CORS, endpoints, or data exposure. Write
“None” if there is no security impact.

## Verification

- [ ] Backend `./mvnw verify` passes
- [ ] Frontend `npm ci && npm run build` passes
- [ ] Anonymous and insufficient-role behavior is tested where applicable
- [ ] Documentation is updated
- [ ] No credentials, tenant data, tokens, or personal information are included

