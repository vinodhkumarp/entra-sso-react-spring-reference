# Developer workflow with Make

The root `Makefile` is the recommended entry point for local development. It keeps component
paths, startup order, environment mapping, build gates, and security commands in one place. Run
all commands in this guide from the repository root.

## Prerequisites

- GNU Make
- Java 25, with `JAVA_HOME` pointing to that JDK when more than one JDK is installed
- Node.js 22.12 or newer and npm
- `curl`, used by the combined run targets for readiness checks

macOS and most Linux development environments provide a POSIX-compatible command shell. On
Windows, use WSL or Git Bash with GNU Make for the combined `make local` and `make entra` targets.
The Makefile automatically selects `mvnw.cmd` when `OS=Windows_NT`; WSL uses the Unix wrapper.

## First checkout

```bash
make setup
```

This command performs the onboarding steps in order:

1. Copies `.env.example` to the ignored `.env` file if `.env` does not already exist.
2. Displays and validates Java, Maven Wrapper, Node.js, npm, and curl.
3. Installs both React applications from their committed npm lockfiles.

It never overwrites an existing `.env`. Inspect every available command at any time with:

```bash
make help
```

## Environment configuration

The Makefile loads the root `.env` automatically. Command-line values take precedence, so a
one-off port change can be made without editing the file:

```bash
make local BACKEND_PORT=8180
```

| Variable               | Required for | Default            | Purpose                                   |
| ---------------------- | ------------ | ------------------ | ----------------------------------------- |
| `ENTRA_TENANT_ID`      | Entra        | None               | Workforce Directory tenant ID             |
| `ENTRA_API_CLIENT_ID`  | Entra        | None               | Protected API application/client ID       |
| `ENTRA_SPA_CLIENT_ID`  | Entra        | None               | React SPA application/client ID           |
| `BACKEND_PORT`         | Both         | `8080`             | Spring backend port                       |
| `ENTRA_UI_PORT`        | Entra        | `5173`             | Production React development-server port  |
| `LOCAL_ISSUER_PORT`    | Local        | `9090`             | Isolated mock issuer port                 |
| `LOCAL_UI_PORT`        | Local        | `5174`             | Isolated token test UI port               |
| `LOCAL_TOKEN_AUDIENCE` | Local        | `local-spring-api` | Audience shared by issuer and backend     |
| `VITE_LOG_LEVEL`       | Entra        | `debug`            | Production UI development logging level   |
| `NVD_API_KEY`          | Security     | Optional           | Improves NVD update speed and reliability |

The Makefile derives issuer URLs, UI origins, JWT audience overrides, and all `VITE_*` variables
from these values. It does not print the NVD key. Do not commit `.env`, access tokens, client
secrets, or vulnerability reports.

View effective non-secret settings and whether identifiers are present with:

```bash
make show-config
```

## Run without Entra

```bash
make local
```

The target launches the three long-running processes concurrently but gates them in the required
readiness order:

1. Local JWT issuer at `http://localhost:9090`
2. Production Spring backend at `http://localhost:8080`
3. Isolated token test UI at `http://localhost:5174`

The logs are interleaved in one terminal. Open `http://localhost:5174` after the ready messages
appear. Press `Ctrl+C` once to stop the group.

For isolated troubleshooting, use three terminals in this order:

```bash
make run-local-issuer
make run-local-backend
make run-local-ui
```

## Run with Microsoft Entra

Fill in these three entries in `.env`:

```dotenv
ENTRA_TENANT_ID=<directory-tenant-id>
ENTRA_API_CLIENT_ID=<api-application-client-id>
ENTRA_SPA_CLIENT_ID=<spa-application-client-id>
```

Then run:

```bash
make entra
```

The Makefile validates the three identifiers, starts the backend against the tenant-specific
Microsoft issuer, waits for backend health, and then starts the MSAL React UI. Open
`http://localhost:5173`. Press `Ctrl+C` to stop the group.

Use separate terminals when investigating one process:

```bash
make run-entra-backend
make run-entra-ui
```

No client secret is needed or accepted by the React application.

## Quality commands

| Command                 | Result                                                         |
| ----------------------- | -------------------------------------------------------------- |
| `make verify`           | Runs all Java and React checks in CI order                     |
| `make coverage`         | Enforces both 85% Java gates and writes JaCoCo HTML reports    |
| `make format`           | Applies Spotless and Prettier formatting                       |
| `make format-check`     | Checks formatting without changing files                       |
| `make generate-openapi` | Validates the contract and regenerates backend OpenAPI sources |
| `make clean`            | Removes known Maven, Vite, coverage, and generated SBOM output |

Individual `verify-*` targets shown by `make help` are useful while iterating on one component.
Before opening a pull request, run the complete gate:

```bash
make verify
```

## Dependency-security commands

| Command                      | Result                                                       |
| ---------------------------- | ------------------------------------------------------------ |
| `make security`              | Runs every Java/Node vulnerability check and generates SBOMs |
| `make security-java`         | Scans the backend and local issuer with Dependency-Check     |
| `make security-node`         | Runs npm audit and generates both React SBOMs                |
| `make security-backend`      | Scans only the production backend                            |
| `make security-local-issuer` | Scans only the local issuer                                  |
| `make security-frontend`     | Audits only the production React UI                          |
| `make security-local-ui`     | Audits only the token test UI                                |
| `make sbom`                  | Generates the four CycloneDX inventories without audit steps |

`make security` is intentionally separate from `make verify`: it contacts external vulnerability
and package services, can take substantially longer on its first run, and may disclose dependency
names and versions. It does not upload source files. `npm audit` sends dependency metadata to the
configured npm registry, while Dependency-Check downloads vulnerability feeds and may use enabled
remote analyzers. Organizations with restricted egress should use approved internal Maven/npm/NVD
mirrors and review the analyzer configuration before running these targets.

Request an optional [NVD API key](https://nvd.nist.gov/developers/request-an-api-key), put it only
in the ignored `.env`, and run:

```bash
make security
```

See [quality-and-security.md](quality-and-security.md) for report locations, suppression policy,
CI behavior, and vulnerability-triage guidance.
